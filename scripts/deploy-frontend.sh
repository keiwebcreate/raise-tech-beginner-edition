#!/bin/bash
# 画面（React）を EC2 に送る（Nginx が配る。Nginx の起動し直しは要らない）
# 使い方（beginner_version で）：bash scripts/deploy-frontend.sh
# 先に npm run lint・npm test が通っていること
source "$(dirname "$0")/common.sh"

echo "== 本番用の画面のファイルを作ります（npm run build）"
(cd "$ROOT_DIR/frontend" && npm run build --silent)

ARCHIVE="$(mktemp -d)/web.tar.gz"
tar -czf "$ARCHIVE" -C "$ROOT_DIR/frontend/dist" .

echo "== EC2（${EC2_HOST}）に送ります"
send "$ARCHIVE" /tmp/taskboard-web.tar.gz
rm -f "$ARCHIVE"

echo "== 入れ替えます"
remote "sudo bash -s" <<'EOF'
set -euo pipefail
# 新しいフォルダに広げてから入れ替える（入れ替えの途中で、ファイルが欠けた画面を見せないため）
rm -rf /opt/taskboard/web.new /opt/taskboard/web.old
install -d -o root -g root -m 755 /opt/taskboard/web.new
tar -xzf /tmp/taskboard-web.tar.gz -C /opt/taskboard/web.new --no-same-owner
chmod -R u=rwX,go=rX /opt/taskboard/web.new
rm -f /tmp/taskboard-web.tar.gz
mv /opt/taskboard/web /opt/taskboard/web.old
mv /opt/taskboard/web.new /opt/taskboard/web
rm -rf /opt/taskboard/web.old
echo "画面を入れ替えました"
EOF
