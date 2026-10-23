#!/usr/bin/env bash
# Restore only the selected virtual-host configuration; keep all blog data.
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo 'Run with sudo.' >&2; exit 1; }
[[ $# -eq 1 ]] || { echo "Usage: sudo bash $0 /var/backups/cyacc-nginx/<timestamp>" >&2; exit 1; }
backup_dir="$(readlink -f -- "$1")"
[[ "$backup_dir" == /var/backups/cyacc-nginx/* && -f "$backup_dir/vhost.conf" && -f "$backup_dir/vhost-path.txt" ]] || { echo 'Invalid backup directory.' >&2; exit 1; }
IFS= read -r vhost < "$backup_dir/vhost-path.txt"
[[ "$vhost" == /etc/nginx/* && -f "$vhost" ]] || { echo 'Original virtual-host path is no longer valid.' >&2; exit 1; }
cp -- "$backup_dir/vhost.conf" "$vhost"
nginx -t
systemctl reload nginx
echo 'Restored the previous site configuration. Blog containers and database volumes were retained.'
