#!/bin/bash
# サーバー（Spring Boot）を EC2 に送って、起動し直す
# 使い方（beginner_version で）：bash scripts/deploy-backend.sh
# 先に ./gradlew check（テストとチェック）が通っていること
source "$(dirname "$0")/common.sh"

echo "== 本番用の jar を作ります（./gradlew bootJar）"
(cd "$ROOT_DIR/backend" && ./gradlew --quiet bootJar)
JAR="$(ls "$ROOT_DIR"/backend/build/libs/*.jar | grep -v -- '-plain\.jar$' | head -n 1)"
echo "   $JAR"

echo "== EC2（${EC2_HOST}）に送ります"
send "$JAR" /tmp/taskboard.jar

echo "== 入れ替えて、起動し直します"
remote "sudo bash -s" <<'EOF'
set -euo pipefail
install -o root -g taskboard -m 640 /tmp/taskboard.jar /opt/taskboard/app/taskboard.jar
rm -f /tmp/taskboard.jar
systemctl restart taskboard

# 起動するまで待つ（最大3分）。/api/auth/me はログインしていないと 401 を返すので、401 が返れば動いている
for i in $(seq 1 36); do
  code="$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/auth/me || true)"
  if [ "$code" = "401" ]; then
    echo "Spring Boot が動き出しました（${i}回目の確認）"
    exit 0
  fi
  sleep 5
done
echo "Spring Boot が3分たっても動き出しません。ログ：sudo journalctl -u taskboard -n 100" >&2
exit 1
EOF
