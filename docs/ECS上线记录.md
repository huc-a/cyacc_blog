# cyacc.cn 上线记录 — 2026-10-23

博客已在 Ubuntu 24.04.5 LTS / x86_64 ECS 上上线，地址为 https://cyacc.cn，后台为 https://cyacc.cn/admin/login。使用现有 Nginx 和证书，原静态站文件保留在 `/var/www/personal-blog`。

## 运行配置

- 部署目录：`/opt/cyacc/releases/ecs-2026-10-23`；Compose 项目：`cyacc-blog`。
- 宿主 Nginx 负责 80/443，前端只绑定 `127.0.0.1:8088`，后端只绑定 `127.0.0.1:8089`。数据库没有发布宿主端口。
- PostgreSQL、Java、Nuxt 均健康；迁移容器正常退出 0。Docker 和 Nginx 已启用开机启动，应用容器采用 `unless-stopped`。
- 生产库重新初始化，四份迁移成功；检查时内容、版本、分类、标签、媒体均为 0，管理员为 1。应用使用 `blog_runtime`，不具备超级用户、建库、建角色或 public schema 建表权限；自动迁移关闭。
- `SITE_URL=https://cyacc.cn`，Cookie 使用 Secure / HttpOnly / SameSite=Lax。
- 证书同时覆盖 cyacc.cn 与 www.cyacc.cn，证书有效期至 2027-01-12 23:59:59 UTC；本次沿用现有证书，没有重新签发或配置续期任务。

## 实际部署中修复的问题

1. ECS 的 Docker Hub 解析异常，绕过 DNS 后直连仍被重置。核实 [DaoCloud 官方镜像服务说明](https://github.com/DaoCloud/public-image-mirror)，验证固定摘要可以完整拉取后，在 Docker daemon 中配置 `https://docker.m.daocloud.io`。没有修改系统 DNS 或固定 hosts IP。
2. 原 Node 24.22.1-alpine 摘要指向不含运行平台的空 OCI 索引。向 Docker Hub 官方 API 核对后，更新为 `sha256:06d7adf3e0ef61a13f1d28682726366f97a15aa1888c934d3ecec86c33918b53`，保留相同 Node 版本。新增 `scripts/check-image-manifests.py` 检查摘要和平台，5 种基础镜像均验证 linux/amd64，旧空索引被明确拒绝。
3. Docker 29 中，前端仅加入 internal 网络时，即使配置端口映射也没有实际监听 8088。ECS 覆盖配置为前端增加 outbound 网络后，确认宿主只监听 127.0.0.1:8088；数据库仍隔离在 private 网络中。

前后端及运维工具镜像已在 ECS 从源代码实际构建。当前部署文件修订标签为 `ecs-2026-10-23.3`；现有运行目录与应用镜像标签仍使用 `ecs-2026-10-23`。旧源码压缩包保持不变，重新部署应使用 `.3` 修订包。

上线后发现生产媒体卷根目录属于 root，博客普通用户无法写入，导致上传 400。已修复属主，增加一次性目录初始化与可写性健康检查。独立卷回归、配置部署前后的真实 HTTPS 上传均通过；测试图片已清理。完整记录见 [媒体上传权限修复](媒体上传权限修复.md)，最新完整备份为 `/var/backups/cyacc/backup-20261023T035106Z/`。

## 验收结果

- 隔离 Compose 项目使用独立随机密码、独立数据库及媒体卷，通过仅本机监听的 9443 HTTPS 端口验收。46 项检查通过，覆盖五类内容的创建、发布、改稿隔离、并发版本冲突、回收恢复、教程章节、上传和媒体引用保护、搜索、CSRF、会话及来源限流。伪造 X-Forwarded-For / Forwarded 不能绕过限制，不同实际来源独立计数。
- 首轮优惠测试未填写来源、优惠内容和条件，被 400 正确拒绝。补齐验收数据，重建本次创建的隔离测试卷后，完整 46 项通过；没有放宽业务校验。
- 实际备份并恢复隔离库：19 张表、5 条内容、1 张图片及文件校验一致。隔离测试容器和临时 Nginx 入口已移除，未将测试数据导入生产。
- 生产 10 项 HTTPS 检查及 HTTP→HTTPS 跳转通过；登录使用生产 `.env` 在服务器内读取的密码，未将密码输出到聊天或本地交付文件。
- 真实 Chrome 桌面 1440 px、手机 390 px 的首页和登录页共 4 项通过：HTTPS 200、静态资源正常、无脚本错误、无横向溢出。首页显示干净库空状态。
- 首次生产备份完成后，应用恢复健康。最终生产库仍无测试内容和媒体。

## 备份与回滚位置

| 用途 | ECS 路径 |
| --- | --- |
| 原 Nginx 配置与证书配置备份 | `/var/backups/cyacc-nginx/20261023T030035Z/` |
| Docker 配置变更前状态 | `/var/backups/cyacc-system/20261023T025724Z/` |
| 首次生产数据库与媒体备份 | `/var/backups/cyacc/backup-20261023T032112Z/` |
| 隔离恢复演练备份 | `/var/backups/cyacc-preflight/acceptance-20261023/` |
| 构建与验收日志 | `/var/log/cyacc-deploy/` |

备份目录仅 root 可访问；数据库、媒体和配置备份没有下载或放入源码包。目录名中的时间使用 UTC。

需要退回原静态站时，在 ECS 执行：

```bash
cd /opt/cyacc/releases/ecs-2026-10-23
sudo bash deploy/rollback-nginx-ecs.sh /var/backups/cyacc-nginx/20261023T030035Z
```

该操作恢复原站路由，保留博客数据。后续手动完整备份：

```bash
cd /opt/cyacc/releases/ecs-2026-10-23
sudo bash deploy/backup.ecs.sh
```

备份会短暂停止应用写入，应选择维护窗口。

## 站长后续操作

管理员账号为 `admin`。初始密码仍只在 ECS 的生产 `.env` 中；可在自己的服务器终端查看，登录后修改：

```bash
sudo grep '^ADMIN_PASSWORD=' /opt/cyacc/releases/ecs-2026-10-23/deploy/.env
```

录入正式站点介绍、联系方式、备案信息、头像、壁纸与文章；干净库不会继承开发环境设置。Umami 未配置，统计模块显示未配置，不显示模拟访问量。

定期备份计划、异地副本和运行告警尚未配置，GitHub 远程仓库与云端 CI 也未接入。本次验证了备份恢复与开机启动配置，没有重启整台 ECS。镜像服务是下载途径，后续更新仍需核验摘要、平台与可用性。
