# API 约定

同域 `/api`，JSON 使用 camelCase。所有管理接口要求管理员会话；写操作附带 `X-CSRF-TOKEN`。错误返回 `{ "message": "可展示的说明" }`，409 代表版本或约束冲突。

## 认证

- `GET /api/auth/csrf` 获取 `{ token, headerName }`，使用会话保存的令牌。
- `POST /api/auth/login` 表单编码 `username/password`，需要 CSRF。成功后重新获取令牌，避免沿用登录前令牌。
- `GET /api/auth/me` 返回账号名与显示名，不返回密码哈希。
- `POST /api/auth/logout` 需要 CSRF，成功 204。

## 内容

统一五类内容端点避免复制 API，类型通过 `type=article|work|tutorial|website|deal` 指定。

- `GET /api/public/contents?type=&q=&page=1&size=10` 仅公开冻结版本，返回 items/total/page/size。
- `GET /api/public/contents/{slug}` 公开详情；不存在、草稿、下线和回收站均为 404。
- `GET /api/admin/contents?type=&status=all&q=&page=1&size=10`，status 支持 all/draft/published/offline/trash；draft 包含已发布但有工作稿的内容。
- `GET /api/admin/contents/{id}` 返回工作稿（无工作稿时返回已发布版本），包括 lockVersion。
- `POST /api/admin/contents` 创建；`PUT /api/admin/contents/{id}` 保存。
- `POST /api/admin/contents/{id}/publish|offline|trash|restore`，请求 `{ lockVersion }`。
- `GET /api/admin/contents/{id}/revisions` 获取历史摘要。

阶段 1 内容请求：`{ type, slug, title, summary, bodyMarkdown, lockVersion, changeNote? }`。创建时 lockVersion=0，后续写操作须使用最新响应中的 lockVersion。slug 和 type 创建后固定。公开响应不返回工作版本指针或编辑锁。

恢复不会自动上线；恢复后需显式发布。公开查询暂不缓存，避免跨页面陈旧内容。
