# 菜牙爱吃菜

一个以雾山、暖纸色和玻璃卡片为视觉语言的个人博客。

技术栈：Nuxt / Vue / TypeScript、Spring Boot / Spring Security、PostgreSQL / MyBatis / Flyway。

- [开发方案](docs/开发方案.md)
- [阶段验收记录](docs/阶段验收记录.md)
- 原始需求和 UI 原型完整保存在 `原始数据/`，不直接修改。

首版前后台已实现文章、作品、教程、网站收藏、优惠资源五类内容，包含版本化发布、媒体库、外观设置、账号管理、统计接入与审计。部署、备份恢复和安全检查工具已提供。

`v1.1.0` 增加向左展开的导航搜索、顶部透明/滚动加深导航、栏目图标、大幅 GIF 头像和联系方式、ICP 备案链接。后台「外观设置」可直接上传壁纸和头像；「系统设置」可修改 GitHub、邮箱和备案号。上传支持 PNG/JPEG/GIF，每张最多 5 MB；GIF 最多 200 帧，累计画面最多 6400 万像素。上传后点击「应用更改」才会公开显示。

`v1.2.0` 将导航和栏目搜索统一为栏内的小输入框，点击按钮展开；整张内容卡片可点击，标题摘要叠在封面上，日期、内容类型、分类与标签位于实色底栏。个人介绍更紧凑，联系方式只显示图标；首页便签更透明，版权及备案信息居中，前台页脚移除后台入口。后台仍通过 `/admin` 直接访问。

需要 Java 17、Maven 3.9、PostgreSQL 18、Chrome；备份工具需要 Python 3.12+。首次安装前端依赖：

```powershell
.\scripts\setup-web.ps1
```

该脚本下载经过校验的项目内 Node 24.22.1 和 pnpm 10.32.1，不修改系统 Node。PostgreSQL 默认位置是 `D:\Tool\Postgres\bin`，其他位置通过环境变量 `PG_BIN` 指定。

本机启动（各命令保持在独立终端运行）：

```powershell
.\scripts\dev-server.ps1
.\scripts\web.ps1 dev
```

前台默认 `http://127.0.0.1:3000`，API 默认 `http://127.0.0.1:8080`。首次启动会在 `.local/admin-credentials.json` 生成开发管理员凭据，该文件被 Git 忽略。数据库使用独立 `.local/pgdata`，不会接管已有 PostgreSQL 实例。

后台入口：<http://127.0.0.1:3000/admin>。修改密码后自行保管新密码，初始化凭据文件不会自动同步。数据、媒体和工具都在 `.local/`，不要为重新安装依赖而删除整个目录。

测试：`scripts/test-server.ps1`、`scripts/web.ps1 typecheck`、`scripts/web.ps1 test`、`scripts/web.ps1 test:e2e`。浏览器测试要求前后端已启动。

生产构建：`scripts/web.ps1 build`；预览：`scripts/web.ps1 preview`。Windows 重建前先在预览终端 Ctrl+C，释放 `.output` 文件。

当前验收：29 项后端测试、3 项 Markdown 测试、13 条 Chrome 流程，类型检查、生产构建和 Windows 数据库冷启动验证通过。v1.1.0 已验证 V3 迁移权限，首版已完成 Nginx 同域转发和真实恢复演练。开发库有 7 条标记为“示例内容”的条目，新环境可显式运行 `node scripts/seed-demo.mjs`；生产不会自动创建示例。测试条目及其引用素材保留在回收站。

- [部署与维护](docs/部署与维护.md)
- [备份恢复](docs/备份恢复.md)
- [安全检查与告警处置](docs/安全检查.md)
- [Git 回滚与 GitHub 提交](docs/版本控制.md)
- [API](docs/API.md)

回滚标签：`v0.0.0-plan`、`v0.1.0-core`、`v0.2.0-services`、`v0.3.0-public`、`v0.4.0-admin`、`v1.0.0`、`v1.1.0`、`v1.2.0`。

尚未配置 GitHub、公网服务器或真实 Umami。Compose/CI 文件已提供；本机无 Docker 引擎，容器启动与云端 CI 需在目标环境验收。依赖扫描的 4 项限定告警及复核期限已记录，不宣称“零漏洞”。
