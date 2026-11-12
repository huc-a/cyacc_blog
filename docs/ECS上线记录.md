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

前后端及运维工具镜像已在 ECS 从源代码实际构建。当前部署文件修订标签与应用镜像标签为 `ecs-2026-10-23.5`，现有运行目录仍使用 `ecs-2026-10-23`。旧源码压缩包保持不变，重新部署应使用 `.5` 修订包。

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

录入正式站点介绍、联系方式、备案信息、头像、壁纸与文章；干净库不会继承开发环境设置。初次上线后已上传 3 个正式媒体文件，后续验收均保留。Umami 已切换至自建，博客后台直接显示统计，无需 Cloud 会员；见 [自建上线与运维](Umami自建上线与运维.md)。

Umami 已配置每日本机备份及 stats.cyacc.cn 证书自动续期，并通过恢复与续期演练；博客完整备份的定时计划、异地副本和运行告警尚未配置，GitHub 远程仓库与云端 CI 也未接入。本次验证了备份恢复与开机启动配置，没有重启整台 ECS。镜像服务是下载途径，后续更新仍需核验摘要、平台与可用性。

## Umami 自建修订 .5

独立 Umami 3.4.0 / PostgreSQL 18.6 已上线至 https://stats.cyacc.cn。默认密码已更换，博客使用团队只读账号的免费 API 密钥，采集与统计图表均通过实际验证。临时网站的 41 次合成采集已清理；正式库记录了真实浏览器验收的 1 PV / 1 UV。修正 v3 路径排行和趋势序列的访客数标注。

当前完整博客备份：`/var/backups/cyacc/backup-20261023T050007Z/`（19 张表、3 个媒体文件）；切换前 Cloud 配置：`/var/backups/cyacc-system/20261023T050007Z-umami-selfhost/.env`。Umami 首份备份：`/var/backups/cyacc-umami/backup-20261023T045842Z/`，实际恢复验证 27 张表。账号获取、资源限制、定时备份与回滚命令见 [运维说明](Umami自建上线与运维.md)。

## 2026-10-30 V8 文章分类与后台资料更新

- 部署版本：`ecs-2026-10-30.1`，运行目录 `/opt/cyacc/releases/ecs-2026-10-30.1`；上一版本 `/opt/cyacc/releases/ecs-2026-10-28.1` 保留。
- 源码包 SHA-256：`207e218c9de2d3ee04886a1243eda30a78b3b7c721d6d33ec1c594c095118147`。
- 更新前完整备份：`/var/backups/cyacc/backup-20261030T092605Z`；备份校验 18 张表、8 个媒体文件，备份脚本完成后服务恢复。
- V8 Flyway 迁移从 v7 成功执行到 v8；migrate 与 media-init 正常退出（0），数据库、服务端、前端容器均为 healthy。没有改动宿主 Nginx 与证书配置。
- HTTPS 冒烟检查：首页、文章页、登录页、管理员资料页均返回 200；匿名管理 taxonomy API 返回 401；公开分类 API 正常。预置“作品、教程、羊毛”当前各 0 篇，文章列表暂只展示“全部”；为文章分配分类后才会出现相应筛选项。
- 上一发布目录和持久化数据卷均保留。应用代码可从旧目录按 Compose 项目回滚；数据库不做降级。数据恢复使用上述 V8 前备份，并应先在隔离库验证。

## 2026-11-02 ECS 更新（ecs-2026-11-02.1）

- 备份：`/var/backups/cyacc/backup-20261102T080132Z`，脚本验证 19 张表、8 个媒体文件；备份过程中应用短暂停写，完成后旧版本服务恢复。
- 新发布目录：`/opt/cyacc/releases/ecs-2026-11-02.1`；前一发布 `ecs-2026-10-30.1` 仍保留，生产 .env 从旧版本复制并仅更新 RELEASE_TAG，数据卷与宿主 Nginx/证书未改。
- 部署源码包 SHA-256：`64b5e28dd15d253fddf757d91c42aaa0bfa7ead439bbbffa135cee3d3146ada4`。包中不含 .env、密钥、媒体、开发依赖或浏览器测试截图。
- Nuxt 生产构建和 Java package 成功；ECS 镜像构建成功，迁移与媒体初始化容器均正常退出（0），数据库、服务端、前端容器 healthy。没有新增数据库迁移。
- HTTPS 冒烟：首页、文章、作品、教程、羊毛、管理登录、管理员资料、系统设置和内容管理均为 200；匿名管理 taxonomy 接口为 401；公开分类接口正常；首页已不显示右侧宣传卡片。
- 构建时本机 Node 22.13 低于项目要求，Nuxt 仍提示版本兼容警告；后端测试与浏览器 E2E 未在本轮重跑。上线前已知文章列表内联搜索自动聚焦的 E2E 项仍待后续处理。

## 2026-11-10 固定文章入口与类型筛选（ecs-2026-11-10.1）

- 新发布目录：`/opt/cyacc/releases/ecs-2026-11-10.1`；上一发布 `ecs-2026-11-02.1` 保留。生产 `.env` 从上一发布复制，权限保持 600，仅更新 `RELEASE_TAG`；复用原 Compose 项目、数据库与媒体卷。宿主 Nginx 和证书没有改动。
- 源码包 SHA-256：`a71e3b79e2ef6bc90280105232f314454084853a96de17fbb952b762cb9161b7`。包中不含本地 `.local`、环境变量文件、私钥、媒体、`node_modules`、构建产物或测试截图。
- 更新前备份 `/var/backups/cyacc/backup-20261110T035908Z`，备份脚本验证 19 张表、8 个媒体文件，并在备份后恢复旧服务。更新时 migrate 与 media-init 正常退出 0；数据库、服务端、前端均为 healthy。Flyway 版本仍为 V8，没有新增迁移。
- 首页、文章、作品/教程/羊毛类型筛选、管理登录 HTTPS 均 HTTP 200；`/works`、`/tutorials`、`/deals` 返回 302 到固定的文章列表并附上对应类型参数。线上 API 返回作品 9 条、教程 3 条；两个筛选页 SSR 均包含相应条目。匿名管理 taxonomy API 返回 401。
- `pnpm typecheck`、Nuxt 生产构建和 Java package（`-DskipTests`）通过。未重跑 Java 集成测试或浏览器 E2E；既有内联搜索自动聚焦 E2E 待修项未纳入本轮。

## 2026-11-12 内容类型收敛与改版上线（ecs-2026-11-12.1）

- 新发布目录：`/opt/cyacc/releases/ecs-2026-11-12.1`；上一发布 `ecs-2026-11-10.1` 保留。生产 `.env` 从上一发布复制（权限 600），仅更新 `RELEASE_TAG` 并新增 `TRUST_FORWARDED_HEADERS=true`（宿主 Nginx 覆盖转发头，登录限流按真实来源计数）。宿主 Nginx、证书、Umami 未改动。
- 源码为本地分支 `optimization-2026-11-12`（14 个提交，含三批优化、前台改版与类型挖除），已打 git 标签 `ecs-2026-11-12.1`。源码包 SHA-256：`91109c27061d9ef09de8f0869ec297fa55fe4cba10c28ee0dfa585df1eee60ed`。包中不含 `.local`、`.env`、媒体与构建产物。
- 更新前完整备份：`/var/backups/cyacc/backup-20261112T105910Z`（部署当晚新建，脚本校验通过，备份后旧服务恢复）。
- 内容转换（先于迁移执行，单事务）：已发布作品 9 篇、教程 3 篇转为文章——作品正文保留并在文末附项目状态/技术栈/源码地址，教程正文由简介+前置要求+全部章节按序合并；两类分别归入既有分类「作品」（id 1）与「教程」（id 2）；空分类「羊毛」（id 3）删除。生产库原无 pg_trgm 扩展（旧版 init-db 未包含），已由超级用户预先 CREATE EXTENSION。
- 首次切换时 V12 失败回滚：回收站内另有 4 篇已删除的作品草稿未转换，触发类型 check 约束；补转后第二次切换成功。期间站点中断约数分钟。Flyway 最终 V8 → V12（V9 索引与旧表清理、V10 pg_trgm 索引、V11 媒体展示图列、V12 删除五张类型表并把 content 类型约束收紧为仅 article、站点 homeModules 收敛为 ["article"]）。
- HTTPS 冒烟：首页、文章列表、转换后的作品/教程文章页、管理登录均 200；`/works` 返回 404；公开 API 已发布总数 38，分类计数 作品 9 / 教程 3。数据库、服务端、前端容器 healthy，migrate 与 media-init 正常退出。
- 已知小问题：详情接口返回 categoryId 但不含 categoryName，详情页分类徽章回退显示「文章」（列表页正常），待后续小版本补齐。
- 回滚：旧发布目录与数据卷保留；数据库不做降级，如需整体回退使用部署前备份恢复。

## 2026-11-12 详情分类名修复（ecs-2026-11-12.2）

- 新发布目录：`/opt/cyacc/releases/ecs-2026-11-12.2`；上一发布 `ecs-2026-11-12.1` 保留。生产 `.env` 从上一发布复制（权限 600），仅更新 `RELEASE_TAG`；宿主 Nginx、证书、Umami 未改动。没有新增数据库迁移，Flyway 仍为 V12。
- 修复内容（提交 `1a11cb8`，已打 git 标签 `ecs-2026-11-12.2`）：公开详情接口此前只返回 categoryId、不含 categoryName，详情页分类徽章只能回退显示「文章」。后端 `ContentService.detail` 按 category_id 回查分类名写入 `categoryName`，`PublicViews.ContentDetail` 白名单新增 `categoryName` 与 `tagNames`；前端 `ContentReader` 元信息行新增分类徽章。后端 49 项测试全过（含新增的公开详情分类断言），前端 typecheck 与 vitest 通过。
- 同步推送 GitHub（huc-a/cyacc_blog）：`main` 由 7d1b571 快进到 1a11cb8，`optimization-2026-11-12` 分支与标签 `ecs-2026-11-12.1`、`ecs-2026-11-12.2` 一并推送——此前改版分支与 .1 标签只在本地，这次起远程与生产一致。
- 源码包 SHA-256：`b9ec29818f8c7010941169054cb5f9f7f19e65933b6d2314129b508b8ecf74f7`。包中不含 `.local`、`.env`、媒体与构建产物。
- 更新前完整备份：`/var/backups/cyacc/backup-20261112T113506Z`（数据库 + 媒体，备份后旧服务恢复）。
- 切换结果：migrate 与 media-init 正常退出（0），数据库、服务端、前端容器 healthy，镜像标签 ecs-2026-11-12.2，本机健康检查通过。
- HTTPS 冒烟：首页、文章列表均 200，已发布总数 41；三篇新作品文章（/articles/46、47、48）详情接口均返回 `categoryName: 作品` 与完整 `tagNames`，详情页 SSR 含分类徽章「作品」。
- 回滚：旧发布目录与数据卷保留；无数据库变更，可直接按 Compose 切回 ecs-2026-11-12.1。
