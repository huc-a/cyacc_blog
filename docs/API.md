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

阶段 2 扩展：内容请求增加 `categoryId/tagIds/coverAssetId/galleryAssetIds/details`。详情按类型保存，教程 `details.chapters` 含 `chapterKey/slug/title/bodyMarkdown`，数组顺序即章节顺序。作品包含 demoUrl/sourceUrl/projectStatus/techStack，网站包含 websiteUrl/iconAssetId，羊毛包含 sourceUrl/claimUrl/benefitText/requirementsMarkdown/startsAt/endsAt。公开列表支持 categoryId/tagId/date（上海时区）。

## 站点与管理

公开内容列表支持 `home=true`，按站点 `homeModules` 限制类型；独立栏目和全站搜索不传此参数。

- `GET /api/public/site` 公开配置；`GET /api/admin/site` 返回 settings 和 lockVersion；`PUT /api/admin/site` 原子更新同结构。未知字段拒绝。
- v1.1.0 站点字段：`avatarAssetId`（可空、引用媒体）、`icpNumber`（最多 80 字符、空值不展示）、`navTopOpacity`（0–1，默认 0）、`navScrolledOpacity`（0.5–1，默认 0.92）。壁纸继续使用 `backgroundAssetId`。保存时传完整 settings 和最新 lockVersion。
- `GET /api/public/taxonomy`、`GET /api/admin/taxonomy`；`POST/PUT/DELETE /api/admin/taxonomy/categories|tags[/{id}]` 管理词汇，已引用词汇不可删除。
- `GET/POST /api/admin/media` 分页查询/上传（multipart 字段 file）；`DELETE /api/admin/media/{id}` 删除未引用素材。
- 图片支持 PNG/JPEG/GIF，输入最多 5 MB、单帧最多 1600 万像素且边长不超过 8000。GIF 最多 200 帧，画布面积乘帧数不超过 6400 万像素。全部重新编码，GIF 保留动画，处理后最多 10 MB；伪造类型和超限返回 400，引用中的素材删除返回 409。
- `GET /api/admin/media/{id}/file` 管理预览；`GET /api/public/media/{id}` 仅公开内容或站点引用的媒体可读取。草稿素材不会提前公开。
- `POST /api/admin/contents/{id}/verify` 人工核验，参数 revisionId/result/evidenceUrl/note，只有当前公开羊毛版本可核验。
- `GET /api/public/summary` 内容汇总和发布日历；`GET /api/admin/dashboard` 工作台；`GET /api/admin/logs?page=1&size=20` 审计分页。
- `PUT /api/admin/account/profile` 参数 displayName/lockVersion；`POST /api/admin/account/password` 参数 currentPassword/newPassword/lockVersion，修改后重新登录，其他会话也失效。
- `GET /api/admin/analytics/overview?start=YYYY-MM-DD&end=YYYY-MM-DD` 最多 366 天，status 为 available/unconfigured/unavailable。Umami 凭据不返回浏览器。
