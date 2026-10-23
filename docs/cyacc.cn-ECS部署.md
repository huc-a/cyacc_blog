# cyacc.cn：Ubuntu 24.04 + 现有 Nginx 部署指南

适用情况：Ubuntu 24.04 LTS、域名 cyacc.cn 已直接解析到 ECS、现有 Nginx 已配置有效 HTTPS 证书；原站是静态空壳。由你在 ECS 终端执行。首次使用干净生产库，不导入本地示例、回收站或测试账号。

部署版本：`ecs-2026-10-23`。下列命令以 Bash 和有 sudo 权限的账号为前提。每一步成功后再进行下一步；失败时保留错误信息，不继续切换网站。

## 结构与边界

```text
访客 → cyacc.cn:443（现有宿主 Nginx / 现有证书）
        ├─ /api/* → 127.0.0.1:8089 → Java 容器
        └─ 其他   → 127.0.0.1:8088 → Nuxt 容器
                                      Java → 私有网络内的 PostgreSQL
```

使用 `compose.yaml` + `compose.ecs.yaml`，停用原方案的容器 Nginx。8088/8089 只绑定本机，数据库不映射宿主端口。宿主 Nginx 覆盖客户端转发头，Java 在这一受限部署模式下解析转发头，使登录限流按真实来源计数。若域名前另有 CDN、WAF 或负载均衡，先按实际入口配置可信代理，不能直接套用本文的 `$remote_addr` 假设。

不修改现有证书路径，不删除原静态站文件。最终只修改当前 cyacc.cn HTTPS 虚拟主机的转发部分，保留回滚配置。

## 1. 检查环境与现有站点

```bash
cat /etc/os-release
uname -m
free -h
df -h /
sudo nginx -t
sudo ss -lntp | grep -E ':80 |:443 |:8088 |:8089 ' || true
sudo nginx -T 2>&1 | grep -n -E 'configuration file|server_name.*cyacc\.cn|ssl_certificate'
```

记下包含 cyacc.cn 的实际配置文件路径；不要猜文件名。确认 8088、8089 未被其他程序占用。若服务器内存较小，Docker 镜像构建可能比运行占用更多内存；构建失败应先看日志和资源，再决定是否在其他机器构建镜像。

阿里云安全组保留你的 SSH 管理入口及 80/443；无需开放 3000、8080、8088、8089、5432。本文不会自动改防火墙或 SSH 配置。

## 2. 安装 Docker Engine 与 Compose 插件

使用 Docker 官方 Ubuntu 软件源；不会替换现有 Nginx。

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl openssl python3
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker version
sudo docker compose version
```

参考：[Docker 官方 Ubuntu 安装说明](https://docs.docker.com/engine/install/ubuntu/)。如果软件源或镜像下载失败，先解决对应网络问题；不要因为下载失败就改用来历不明的镜像或安装脚本。

## 3. 上传部署包，生成生产配置

将提供的 `cyacc-ecs-2026-10-23.tar.gz` 和 `cyacc-ecs-2026-10-23.sha256` 上传至 ECS 的 `/tmp/`，可使用你现有的 SFTP/SSH 文件传输工具。部署包只有版本控制中的源码、配置和文档，不含开发库、密码、上传图片或备份。

```bash
cd /tmp
sha256sum -c cyacc-ecs-2026-10-23.sha256
```

必须显示 `OK` 后再解压。目标版本目录应当不存在；若已存在，先核对是否执行过部署，不要删除或覆盖它。

```bash
sudo install -d -m 0755 /opt/cyacc/releases
sudo mkdir /opt/cyacc/releases/ecs-2026-10-23
sudo tar -xzf /tmp/cyacc-ecs-2026-10-23.tar.gz \
  -C /opt/cyacc/releases/ecs-2026-10-23 --no-same-owner
cd /opt/cyacc/releases/ecs-2026-10-23
sudo bash deploy/init-ecs-env.sh
sudo install -d -m 0700 /var/backups/cyacc
```

初始化脚本创建权限为 600 的 `deploy/.env`，生成四个独立随机密码，并设置 `SITE_URL=https://cyacc.cn`、Secure Cookie、备份目录和固定 Compose 项目名。已有 `.env` 时会拒绝覆盖。生产管理员默认账号 `admin`，初始密码只保存在这台 ECS 的 `.env` 中。

如果重新解压或升级，保留原 `.env` 和 Compose 项目名；修改 `.env` 的数据库密码不会自动修改已有数据库角色密码。不要运行示例数据初始化脚本。

## 4. 先启动博客，暂不切换静态站

以下命令都在 `/opt/cyacc/releases/ecs-2026-10-23` 执行；始终带两个 Compose 文件。

```bash
(
  set -euo pipefail
  compose=(sudo docker compose -f deploy/compose.yaml -f deploy/compose.ecs.yaml --env-file deploy/.env)
  "${compose[@]}" config --quiet
  "${compose[@]}" pull db
  "${compose[@]}" build migrate web
  "${compose[@]}" up -d --pull never --wait --wait-timeout 300
  "${compose[@]}" ps -a
)
```

这是一个遇错停止的子 Shell：下载或构建失败时不会继续启动。只有整个代码块成功后再运行下方健康检查。若收到 `registry-1.docker.io ... i/o timeout`，先按 [镜像拉取故障处理](ECS镜像拉取故障处理.md) 检查服务器网络；后续连接失败与 JSON 解析失败不能作为应用故障判断。已生成的 `deploy/.env` 应保留，不必重新初始化。

补充提供的 `deploy/start-ecs.sh` 包含相同的启动流程和 HTTP 健康检查；如原始源码包中没有此脚本，可单独下载并放入现有版本的 `deploy/`，用 `sudo bash deploy/start-ecs.sh` 执行。它不会修改现有密码、Nginx 配置或清空数据卷。

数据库、server、web 应为运行/健康状态；migrate 正常完成后是 `Exited (0)`，不是需要常驻的服务。容器 Nginx 不应启动；不要启用 `container-proxy` profile。

```bash
curl -fsS http://127.0.0.1:8089/api/public/site
curl -fsS http://127.0.0.1:8089/api/public/contents \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["total"] == 0, d["total"]; print("干净生产库：0 条公开内容")'
curl -fsS -o /dev/null -w '前端 HTTP %{http_code}\n' http://127.0.0.1:8088/
sudo ss -lntp | grep -E ':8088 |:8089 '
```

应得到正常站点配置、0 条公开内容、前端 HTTP 200，且两个端口只在 127.0.0.1 监听。HTTP 本机入口只用于健康检查；不要关闭 Secure Cookie 来登录，管理员登录使用最终 HTTPS 地址。

继续核验真实数据库连接、四份迁移、运行权限及全部内容表为空（包括草稿和回收站）。以下检查不修改数据，也不打印数据库密码：

```bash
sudo docker compose -f deploy/compose.yaml -f deploy/compose.ecs.yaml \
  --env-file deploy/.env exec -T db sh -c \
  'PGPASSWORD="$DB_PASSWORD" exec psql -h 127.0.0.1 -U blog_runtime -d caiya -v ON_ERROR_STOP=1' <<'SQL'
SELECT current_user, current_database();
DO $$
BEGIN
  IF current_user <> 'blog_runtime' OR EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = current_user
    AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolbypassrls)
  ) OR has_schema_privilege(current_user, 'public', 'CREATE') THEN
    RAISE EXCEPTION 'Runtime role has unexpected privileges';
  END IF;
  IF NOT (has_table_privilege(current_user, 'content', 'SELECT')
      AND has_table_privilege(current_user, 'content', 'INSERT')
      AND has_table_privilege(current_user, 'content', 'UPDATE')
      AND has_table_privilege(current_user, 'content', 'DELETE')) THEN
    RAISE EXCEPTION 'Runtime CRUD privileges are missing';
  END IF;
  IF (SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL) <> 4 THEN
    RAISE EXCEPTION 'Expected four successful migrations';
  END IF;
  IF (SELECT count(*) FROM content) <> 0
     OR (SELECT count(*) FROM content_revision) <> 0
     OR (SELECT count(*) FROM media_asset) <> 0
     OR (SELECT count(*) FROM category) <> 0
     OR (SELECT count(*) FROM tag) <> 0
     OR (SELECT count(*) FROM admin_user) <> 1 THEN
    RAISE EXCEPTION 'Expected a fresh production database with one admin';
  END IF;
END $$;
SELECT 'PASS: clean database, migrations and runtime role' AS result;
SQL
```

最后必须出现 `PASS`。这项空库检查只在首次录入内容前运行；失败时先排查已有数据卷或初始化日志，不要删除数据卷重试。

失败时查看：

```bash
sudo docker compose -f deploy/compose.yaml -f deploy/compose.ecs.yaml \
  --env-file deploy/.env logs --tail=100 migrate server web
```

## 5. 备份 Nginx 配置并准备转发

先确认 `/etc/nginx/nginx.conf` 的 `http {}` 中加载了 `/etc/nginx/conf.d/*.conf`；Ubuntu 标准配置通常已有这一行。限流配置需要在 http 上下文加载一次，不能放进 server 上下文。

把下面的文件路径换成第 1 步确认的真实路径：

```bash
sudo bash deploy/prepare-nginx-ecs.sh /etc/nginx/sites-available/你的现有站点文件
```

脚本会备份当前虚拟主机和完整 Nginx 配置，安装三个新配置片段并运行 `nginx -t`；**不会修改虚拟主机、reload 或切换访问**。已有同名片段时会停止，避免覆盖既有配置。记下输出的 `/var/backups/cyacc-nginx/<时间>/`，这是回滚目录；备份可能包含敏感配置，应只留在服务器上。

## 6. 切换 cyacc.cn

编辑脚本输出的真实虚拟主机文件：

```bash
sudoedit /etc/nginx/sites-available/你的现有站点文件
```

在已有的 **cyacc.cn HTTPS `server {}`** 中：

- 保留 `listen 443 ssl`、`server_name`、证书、私钥路径及现有 TLS 设置。
- 保留 HTTP→HTTPS 跳转和证书续期的 `/.well-known/acme-challenge/` 处理。
- 替换旧静态站的 `location /`，并移除与新代理冲突的 `/api/` 或静态扩展名正则 location；否则旧规则可能把 Nuxt 的 JS/CSS 请求送到静态目录。
- 若当前 server 已有 `client_max_body_size`，移除重复指令或只保留一处 6m。
- 添加下面一行，不能放到 server 外部：

```nginx
include /etc/nginx/snippets/cyacc-blog-locations.conf;
```

不要另外新建一个重复的 cyacc.cn HTTPS server，也不要把证书路径替换成猜测值。保存后执行：

```bash
sudo nginx -t && sudo systemctl reload nginx
```

只有测试通过才 reload；无需停止 Nginx。这一步完成后 cyacc.cn 开始访问新博客。原静态文件仍然保留。

## 7. HTTPS 与功能验收

```bash
curl -I http://cyacc.cn/
curl -I https://cyacc.cn/
curl -s -o /dev/null -w '匿名管理接口 HTTP %{http_code}\n' \
  https://cyacc.cn/api/admin/contents
python3 - <<'PY'
import urllib.request
with urllib.request.urlopen('https://cyacc.cn/api/auth/csrf', timeout=15) as r:
    cookies = r.headers.get_all('Set-Cookie', [])
    assert any('JSESSIONID=' in c and 'secure' in c.lower() and 'httponly' in c.lower()
               and 'samesite=lax' in c.lower() for c in cookies)
print('证书验证成功；会话 Cookie 的 Secure / HttpOnly / SameSite=Lax 正常')
PY
```

预期 HTTP 跳转 HTTPS、HTTPS 首页 200、匿名管理接口 401。Python 检查不会打印会话 Cookie。然后只在你自己的 ECS 终端查看初始管理员密码，不要把它粘贴到聊天：

```bash
sudo grep '^ADMIN_PASSWORD=' deploy/.env
```

打开 `https://cyacc.cn/admin/login`，账号为 `admin`。先完成以下操作，再录入正式内容：

1. 登录后修改初始密码；确认需要重新登录，旧会话不能继续访问后台。以后 `.env` 中的初始密码不会自动同步为新密码。
2. 系统设置填写站点介绍、联系方式、ICP、实际公安备案状态；外观设置上传正式头像和壁纸。干净库不会继承本地这些设置。
3. 新建一条验收文章，保存草稿后确认匿名不可见；发布后可见；改稿未发布时前台仍是旧内容。
4. 检查上传、手机布局、搜索、教程章节、下线和回收恢复；把验收内容回收。
5. 确认第 4 步数据库权限与干净库检查已通过，应用配置仍使用 `blog_runtime`。
6. 在预发布验收时验证登录限流：同一来源频繁失败应被限制，不同来源应分开计数，伪造 X-Forwarded-For 不能绕过。不要在自己需要登录时反复触发限流。

本地已验证 Java 解析可信转发地址并分开计数；Nginx 的转发头覆盖配置已通过语法检查。Docker NAT、现有服务器配置与实际来源链仍需在 ECS 验收；若入口有 CDN/WAF，应先改可信代理配置。

## 8. 首次备份与后续维护

正式内容录入后，选择短暂维护窗口执行：

```bash
cd /opt/cyacc/releases/ecs-2026-10-23
sudo bash deploy/backup.ecs.sh
```

此 ECS 专用脚本在备份期间停止 Java/Nuxt 写入，宿主 Nginx 保持运行；博客可能短暂返回 502，完成或异常退出后会尝试重新启动应用。它不会启用容器 Nginx。不要混用默认 `deploy/backup.sh`。

备份保存在 `/var/backups/cyacc`，包含数据库、媒体和校验清单。完成后再次确认首页和后台健康，把备份加密保存到 ECS 之外，并按 `docs/备份恢复.md` 在隔离环境演练恢复。定期备份、保留周期与告警尚需配置；仅在同一块 ECS 磁盘保存副本不够。

Umami 初次可留空，博客其他功能可用；访问统计会明确显示未配置。需要统计时再配置真实实例并验收。

## 9. 立即回滚到原静态站

若切换后异常，使用第 5 步实际输出的备份目录：

```bash
cd /opt/cyacc/releases/ecs-2026-10-23
sudo bash deploy/rollback-nginx-ecs.sh /var/backups/cyacc-nginx/实际时间目录
```

脚本恢复原虚拟主机配置，测试通过后 reload；原静态站恢复访问。博客容器、数据库及图片继续保留，便于排查后再次切换。不要执行 `docker compose down -v`，它会删除数据卷。

## 本次准备与验证范围

已准备：ECS Compose 覆盖配置、现有 Nginx 配置片段、生产随机密码初始化、Nginx 备份/回滚、ECS 备份脚本。部署包基于已通过应用测试的版本，无开发数据。

本机已通过：合并 Compose 配置解析与端口/角色检查、脚本语法、四个独立随机密码与拒绝重复初始化、Nginx 配置语法、真实 Java 服务的可信转发地址分来源限流测试。临时本机 Nginx 进程启动被自动审批拦截（仅返回 `blocked by policy`），所以没有把代理端到端测试记为通过。本机也没有 Docker Engine，实际镜像构建/容器启动、完整代理链及现有证书仍由以上 ECS 步骤验收。
