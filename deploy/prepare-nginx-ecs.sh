#!/usr/bin/env bash
# Back up an existing site and install snippets, without switching traffic.
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo 'Run with sudo.' >&2; exit 1; }
[[ $# -eq 1 ]] || { echo "Usage: sudo bash $0 /etc/nginx/sites-available/your-existing-site" >&2; exit 1; }
vhost="$(readlink -f -- "$1")"
[[ "$vhost" == /etc/nginx/* && -f "$vhost" ]] || { echo 'Expected an existing file under /etc/nginx.' >&2; exit 1; }
grep -Fq 'cyacc.cn' "$vhost" || { echo 'The selected file does not contain cyacc.cn.' >&2; exit 1; }
source_dir="$(cd "$(dirname "$0")/nginx" && pwd)"
targets=(/etc/nginx/snippets/cyacc-proxy-headers.conf /etc/nginx/snippets/cyacc-blog-locations.conf /etc/nginx/conf.d/cyacc-blog-rate-limit.conf)
for path in "${targets[@]}"; do
  [[ ! -e "$path" && ! -L "$path" ]] || { echo "Already exists, review manually: $path" >&2; exit 1; }
done
nginx -t
umask 077
backup_dir="/var/backups/cyacc-nginx/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p /var/backups/cyacc-nginx
chmod 700 /var/backups/cyacc-nginx
mkdir -m 700 "$backup_dir"
cp -p -- "$vhost" "$backup_dir/vhost.conf"
chmod 600 "$backup_dir/vhost.conf"
printf '%s\n' "$vhost" > "$backup_dir/vhost-path.txt"
nginx -T > "$backup_dir/nginx-T-before.txt" 2>&1
tar -C /etc -czf "$backup_dir/nginx-config-before.tar.gz" nginx
printf 'Configuration backup: %s\n' "$backup_dir"
install -d -m 755 /etc/nginx/snippets /etc/nginx/conf.d
install -m 644 "$source_dir/cyacc-proxy-headers.conf" "${targets[0]}"
install -m 644 "$source_dir/cyacc-blog-locations.conf" "${targets[1]}"
install -m 644 "$source_dir/cyacc-blog-rate-limit.conf" "${targets[2]}"
nginx -t
printf 'Prepared only; no reload and no traffic switch.\nEdit the existing HTTPS server in: %s\nBackup directory: %s\n' "$vhost" "$backup_dir"
