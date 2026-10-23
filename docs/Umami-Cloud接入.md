# Umami Cloud 接入

Cloud 的访问采集与 API 读取是两件独立的事。仅使用免费追踪时，可在 Cloud 控制台查看统计，无需在 ECS 安装 Umami 或新增数据库，也无需购买 API 权限。

## 免费采集

在 Umami Cloud 注册、添加网站，从网站的 Tracking code 复制脚本地址与网站 ID。写入服务器 `deploy/.env`：

```dotenv
UMAMI_SCRIPT_URL=https://cloud.umami.is/script.js
UMAMI_WEBSITE_ID=从追踪代码取得的网站ID
UMAMI_API_URL=https://api.umami.is/v1
UMAMI_TOKEN=
```

`UMAMI_API_URL` 是预留的统计读取端点，没有密钥时不会调用 Cloud API。服务器已有其他 Umami 配置时应先核对用途，不要覆盖其他网站的密钥。

使用本修订版应用镜像，重新创建前后端以加载环境变量（单独 restart 不会更新环境变量）：

```bash
cd /opt/cyacc/releases/ecs-2026-10-23
docker compose -f deploy/compose.yaml -f deploy/compose.ecs.yaml --env-file deploy/.env \
  up -d --no-deps --wait --wait-timeout 180 server web
```

博客已有统一追踪插件，不要再将同一 script 粘贴到模板，以免重复统计。插件关闭脚本自动采集，手动追踪前台路由，排除 `/admin`，去除 URL 查询与哈希，来源仅保留 origin，并尊重浏览器 Do Not Track。脚本加载与页面完成事件不会对同一次导航重复计数。

配置 Cloud 脚本且没有 API 密钥时，博客后台“数据统计”展示 Cloud 入口；不会显示模拟图表，也不会将缺少 API 权限描述为服务故障。采集请求成功与控制台最终显示数据分别验证，广告拦截、Do Not Track、网络可达性会影响采集。

## 可选：博客后台读取图表

获得 Cloud API 权限后，在 Cloud 的个人 Settings → API keys 创建密钥，仅在服务器 `deploy/.env` 的 `UMAMI_TOKEN` 保存，再重新创建后端。不要将密钥发到聊天、写入公开变量或提交 Git。

`UMAMI_API_URL` 支持完整端点 `https://api.umami.is/v1`，也支持官方区域路径 `/v1/us`、`/v1/eu`；默认采用 API key 所属账号的数据区域。鉴权为 `Authorization: Bearer <API key>`。

应用兼容新版数值型统计与旧版 `{value, prev}` 格式；新版排行榜使用 `type=path`。保持博客自身 API 结构不变，图表继续使用北京时间。无权限、错误响应和真实零访问分别处理。

自建旧配置仍可使用 `UMAMI_URL=https://stats.example.com`，应用在它后面追加 `/api`。设置 `UMAMI_API_URL` 后优先使用该完整 API 端点。

官方说明：[采集代码](https://docs.umami.is/docs/collect-data)、[Cloud API](https://docs.umami.is/docs/cloud/api-key)、[新版统计接口](https://docs.umami.is/docs/api-reference/get-website-stats)。
