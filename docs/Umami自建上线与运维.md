# Umami 自建上线与运维

2026-10-23 已在现有 ECS 上部署 Umami 3.4.0，博客已切换到自建采集及免费统计 API，无需 Umami Cloud 会员。应用镜像修订为 `ecs-2026-10-23.5`。Cloud 原站点和历史数据保留，没有自动迁移到自建库。

## 使用入口和账号

- 日常查看：[博客后台数据统计](https://cyacc.cn/admin/analytics)，沿用博客管理员账号。
- Umami 管理：[stats.cyacc.cn](https://stats.cyacc.cn/login)，管理员账号 `admin`，密码独立于博客，已替换默认密码。
- 在自己的 ECS 终端执行以下命令查看 Umami 登录资料；不要将结果粘贴到聊天或公开仓库：

```bash
sudo cat /opt/cyacc/umami/admin-credentials.json
```

该文件中的 `password` 即登录密码。自行改密后请保管新密码，此初始化文件不会自动同步。博客使用另一个 `cyacc-blog-reader` 账号生成的 API 密钥；该账号是全局只读用户、站点团队只读成员，不能创建、修改或删除网站。管理员改密不影响博客的只读集成账号。

## 实际部署

| 项目 | 配置 |
| --- | --- |
| 独立目录 / Compose 项目 | `/opt/cyacc/umami` / `cyacc-umami` |
| 应用监听 | `127.0.0.1:3010`，由宿主 Nginx 提供 HTTPS |
| PostgreSQL | 18.6，独立容器和数据卷，无宿主端口 |
| Umami 内存 / 内存加交换上限 | 384 MiB / 512 MiB，Node 堆上限 192 MiB |
| 数据库内存 / 内存加交换上限 | 192 MiB / 256 MiB |
| 数据库权限 | 应用使用普通 `umami` 角色，非超级用户；拥有自己的数据库 |
| HTTPS | Let's Encrypt，证书到期 2027-01-21；`certbot.timer` 自动续期，续期后检查并重载 Nginx |
| 防护 | 随机独立密钥、非 root 应用、丢弃 capabilities、只读 API 账号、登录与采集限流、覆写客户端转发头 |

官方镜像固定摘要为 `sha256:85909afc45bdcda1917394594a087421fdbb05610fded0fa9f6fb861abb2f367`。ECS 直连 GHCR 下载部分分层停滞，改用 `ghcr.m.daocloud.io/umami-software/umami:3.4.0`，保持同一已核验摘要，未更改全局 DNS。默认 Compose 仍指向官方 GHCR，生产 `.env` 的 `UMAMI_IMAGE` 覆盖下载地址。

此 ECS 未启用 CPU cgroup controller，不支持 Docker CPU 配额和 shares，因此没有宣称 CPU 隔离；内存和进程数限制已生效。首次启动发生过内存上限触发的回收，未出现 OOM。最终快照：Umami 约 284 MiB、独立数据库约 25 MiB，主机可用内存约 589 MiB，已有交换使用约 352 MiB。当前适合低流量博客，短测不能证明长期或高并发容量；流量增长时建议升级至 4 GiB 内存或将统计服务移出博客主机。暂未开启会话录制。

## 验收证据

- 后端 5 项统计适配测试、前端 6 项单元测试、类型检查以及两份真实生产镜像构建通过。
- 独立临时网站采集 41 次事件，2 路并发，确认 41 PV、1 UV、趋势和各维度排行；采集平均约 13 ms、最大约 22 ms。同步 12 次博客首页/API 探测均成功，耗时 7–56 ms。属于同机小规模功能验证，不是公网容量压测。
- 默认密码和匿名统计访问被拒绝；只读密钥可读数据，创建、更新和删除权限被拒绝。临时网站及全部合成数据已删除。
- 正式切换后，浏览器访问首页产生 1 PV / 1 UV，博客后台实际显示图表和 `/` 页面排行。后台访问没有被采集，浏览器未发现脚本错误。正式统计从切换时开始，包含这一次验收访问。
- 修正 Umami 3 统计口径：路径排行和 `pageviews.sessions` 序列为访客数，页面不再标为浏览量或会话数；跨日期的小时格子是小时访客数累加，不当作整个区间 UV。
- 博客 HTTPS 登录、真实 PNG 上传、私有图片读取和本次测试图片清理通过，原有 3 个媒体文件保留。
- Umami 数据库备份实际恢复至临时数据库：27 张表、1 个正式网站；恢复库已删除。证书续期演练及 Nginx reload hook 成功。

## 备份与日常检查

Umami 每天北京时间 04:20 后随机延迟最多 10 分钟备份，由 `cyacc-umami-backup.timer` 执行；停机错过后会补执行。仅备份到本机，尚无异地副本，也没有自动清理旧备份。定期检查磁盘并将备份复制到自己的安全存储。

```bash
sudo systemctl status cyacc-umami-backup.timer certbot.timer --no-pager
sudo bash /opt/cyacc/umami/backup.sh
sudo python3 /opt/cyacc/umami/verify-restore.py
docker stats --no-stream
free -m
vmstat 1 5
```

`backup.sh` 使用 PostgreSQL 一致性快照，不停止博客；备份包含统计库、数据库及管理员恢复凭据，仅 root 可读。`verify-restore.py` 在独立 Umami 数据库容器里创建随机命名临时库，恢复最近完整备份并清理临时库，不覆盖正式库。大数据量恢复演练应安排在低峰期。

重要位置：

- Umami 首次完整备份：`/var/backups/cyacc-umami/backup-20261023T045842Z/`。
- 切换前博客完整备份：`/var/backups/cyacc/backup-20261023T050007Z/`，19 张表、3 个媒体文件。
- 原 Cloud 配置：`/var/backups/cyacc-system/20261023T050007Z-umami-selfhost/.env`。
- Nginx 配置变更前备份：`/var/backups/cyacc-system/20261023T045509Z-umami-nginx/`。
- 当前配置和密钥：`/opt/cyacc/umami/.env`、`integration.json`；不要下载到源码包。
- 日志：`/var/log/cyacc-deploy/`、`/var/log/nginx/cyacc-umami-*.log`。

主域名 `cyacc.cn` 原有证书仍沿用原方案，本次 certbot 自动续期仅覆盖 `stats.cyacc.cn`。博客完整备份仍需运行原 `deploy/backup.ecs.sh`，本次新增定时器只备份 Umami。

## 回滚到 Cloud

下列命令只恢复统计配置及应用镜像标签，保留其他后来修改的生产环境变量、数据库和媒体。Cloud 免费模式仍只采集，博客后台恢复为 Cloud 控制台入口。

```bash
sudo python3 - <<'PY'
from pathlib import Path
import os
os.umask(0o077)
old=Path('/var/backups/cyacc-system/20261023T050007Z-umami-selfhost/.env')
current=Path('/opt/cyacc/releases/ecs-2026-10-23/deploy/.env')
values=dict(x.split('=',1) for x in old.read_text().splitlines() if x and not x.startswith('#') and '=' in x)
keys={'RELEASE_TAG','UMAMI_URL','UMAMI_API_URL','UMAMI_WEBSITE_ID','UMAMI_TOKEN','UMAMI_SCRIPT_URL'}
assert values['UMAMI_SCRIPT_URL']=='https://cloud.umami.is/script.js'
Path('/opt/cyacc/umami/blog-before-cloud-rollback.env').write_bytes(current.read_bytes())
lines=[x for x in current.read_text().splitlines() if x.split('=',1)[0] not in keys]
lines.extend(k+'='+values.get(k,'') for k in sorted(keys))
temporary=current.with_name('.env.rollback-next')
temporary.write_text('\n'.join(lines)+'\n');temporary.chmod(0o600);temporary.replace(current)
PY
cd /opt/cyacc/releases/ecs-2026-10-23
sudo docker compose -f deploy/compose.yaml -f deploy/compose.ecs.yaml --env-file deploy/.env up -d --no-deps --wait --wait-timeout 180 server web
```

确认博客恢复后，可以停止 Umami 以释放内存（数据卷保留）：

```bash
sudo systemctl disable --now cyacc-umami-backup.timer
cd /opt/cyacc/umami
sudo docker compose --env-file .env stop
```

停止后统计管理站将暂不可用。需要恢复自建时，先启动其容器，再将保存的自建统计配置加载回博客。不要执行 `docker compose down -v`，它会删除统计数据卷。

## 更新参考

源代码和版本：[Umami 3.4.0](https://github.com/umami-software/umami/releases/tag/v3.4.0)；[官方安装文档](https://docs.umami.is/docs/install)。更新前备份，核验镜像摘要及架构，在隔离库验证迁移；不要直接使用浮动 `latest` 更新生产。
