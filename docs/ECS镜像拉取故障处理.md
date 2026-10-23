# ECS 镜像拉取超时：检查与继续部署

2026-10-23 的 ECS 日志显示：生产 `.env` 已成功生成，Docker Compose 配置解析成功，但 Docker Hub 基础镜像的元数据请求超时，实际请求地址为 `31.13.83.2:443`。构建失败，容器列表为空；后续 8088/8089 连接失败，空响应引起 JSON 解析异常。此时数据库初始化尚未完成。

先保留现有 `.env`、版本目录和 Nginx 配置，不运行重新初始化、清理数据卷或流量切换命令。下面的检查只读取 DNS、hosts 和 HTTP 连通性，不读取密码或修改配置。

```bash
timeout 10 getent ahostsv4 registry-1.docker.io auth.docker.io
resolvectl dns
grep -nE 'docker\.io|31\.13\.83\.2' /etc/hosts || true
curl --noproxy '*' -sS -o /dev/null --connect-timeout 10 --max-time 20 \
  -w 'registry HTTP=%{http_code} remote=%{remote_ip}\n' https://registry-1.docker.io/v2/
curl --noproxy '*' -sS -o /dev/null --connect-timeout 10 --max-time 20 \
  -w 'auth HTTP=%{http_code} remote=%{remote_ip}\n' \
  'https://auth.docker.io/token?service=registry.docker.io&scope=repository:library/node:pull'
```

`registry HTTP=401` 是仓库入口正常响应认证挑战，`auth HTTP=200` 表示匿名拉取认证入口连通。令牌响应被丢弃，不会打印。`HTTP=000` 或超时需要先处理网络；这两个入口正常仍不代表镜像层下载一定成功，以实际 `docker pull` 为准。以上 curl 明确测试直连，不使用 Shell 代理。

解析地址异常需要结合当前 DNS 和 hosts 判断，不能仅凭一个超时就断定根因。不要把某个 Docker Hub IP 固定到 hosts，也不要关闭 HTTPS 校验。若直连确实受限，可使用有权限且能访问的可信镜像仓库，或在可访问 Docker Hub 的环境构建后导入；镜像加速或代理是否适用，需要检查实际可用地址和服务限制，不能保证随意添加一个公共加速域名就能工作。

## 网络恢复后的重试

下载补充的 `start-ecs.sh`，上传到 ECS 的 `/tmp/start-ecs.sh`。在服务器执行：

```bash
sudo install -m 0644 /tmp/start-ecs.sh /opt/cyacc/releases/ecs-2026-10-23/deploy/start-ecs.sh
cd /opt/cyacc/releases/ecs-2026-10-23
sudo bash deploy/start-ecs.sh
```

脚本先检查配置并拉取数据库镜像，再构建应用，成功后才启动和检查健康状态。任一步失败立即退出，保留失败状态；不会生成密码、修改 Nginx 或删除数据。完全成功后回到部署指南第 4 步完成数据库断言，再准备 Nginx 切换。

上述为初次诊断步骤。后续用户授权 SSH 操作后，已确认绕过 DNS 仍无法直连；核实 [DaoCloud 官方镜像服务说明](https://github.com/DaoCloud/public-image-mirror)，实测固定摘要能完整拉取后，在现有 Docker 配置中合并 `registry-mirrors: ["https://docker.m.daocloud.io"]`，校验配置并重启 Docker。该 ECS 已完成配置，无需重复操作；原 Docker 配置状态保存在 `/var/backups/cyacc-system/20261023T025724Z/`。

实际构建又发现旧 Node 摘要是空索引，已使用与 Docker Hub 官方 API 核对一致、包含 linux/amd64 的新固定摘要修复，见 `ecs-2026-10-23.2` 修订包。前后端和运维镜像均已成功构建，网站已上线。不要继续使用最初源码包中的旧 Node 摘要，也不要为处理网络问题删除生产数据卷。镜像服务并非可用性保证，后续更新仍应以真实拉取、摘要和平台核验为准。
