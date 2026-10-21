# 菜牙爱吃菜

一个以雾山、暖纸色和玻璃卡片为视觉语言的个人博客。

技术栈：Nuxt / Vue / TypeScript、Spring Boot / Spring Security、PostgreSQL / MyBatis / Flyway。

- [开发方案](docs/开发方案.md)
- [阶段验收记录](docs/阶段验收记录.md)
- 原始需求和 UI 原型完整保存在 `原始数据/`，不直接修改。

目前后端服务与公开前台已通过阶段验收，管理后台正在建设。

本机启动（各命令保持在独立终端运行）：

```powershell
.\scripts\dev-server.ps1
.\scripts\web.ps1 dev
```

前台默认 `http://127.0.0.1:3000`，API 默认 `http://127.0.0.1:8080`。首次启动会在 `.local/admin-credentials.json` 生成开发管理员凭据，该文件被 Git 忽略。数据库使用独立 `.local/pgdata`，不会接管已有 PostgreSQL 实例。

测试：`scripts/test-server.ps1`、`scripts/web.ps1 typecheck`、`scripts/web.ps1 test`、`scripts/web.ps1 test:e2e`。浏览器测试要求前后端已启动。
