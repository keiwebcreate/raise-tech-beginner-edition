#!/bin/bash
# EC2 の準備（最初に1回。EC2 を作り直したときや、deploy/ec2/ の設定を変えたときも実行する。何度実行してもよい）
# - アプリ専用のユーザー taskboard とフォルダ（/opt/taskboard）
# - Spring Boot の設定（/etc/taskboard/taskboard.env。Terraform の出力から作る。パスワードは入れない）
# - Spring Boot の起動（systemd）、Nginx の設定、スワップ（メモリが足りないときにディスクで補う）
# 使い方（beginner_version で）：bash scripts/setup-server.sh
source "$(dirname "$0")/common.sh"

DB_ENDPOINT="$(terraform -chdir="$TF_DIR" output -raw db_endpoint)"
PARAMETER_NAME="$(terraform -chdir="$TF_DIR" output -raw app_db_password_parameter)"
REGION="$(terraform -chdir="$TF_DIR" output -raw region)"

echo "== EC2（${EC2_HOST}）に設定ファイルを送ります"
remote "rm -rf /tmp/taskboard-setup && mkdir -p /tmp/taskboard-setup"
for f in nginx.conf taskboard-headers.conf taskboard.service start.sh create-app-db-user.sh; do
  send "$ROOT_DIR/deploy/ec2/$f" "/tmp/taskboard-setup/$f"
done

echo "== EC2 で準備します"
remote "sudo bash -s" <<EOF
set -euo pipefail
cd /tmp/taskboard-setup

# RDS のユーザーを作るときに使う psql（まだなければ入れる）
command -v psql >/dev/null || dnf -y install postgresql17

# スワップ 1GB（メモリ 1GB の EC2 で、Java がメモリ不足で落ちないように）
if [ ! -f /swapfile ]; then
  dd if=/dev/zero of=/swapfile bs=1M count=1024 status=none
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
swapon --show | grep -q /swapfile || swapon /swapfile

# アプリ専用のユーザー（ログインはできない）とフォルダ
id taskboard >/dev/null 2>&1 || useradd --system --home-dir /opt/taskboard --shell /sbin/nologin taskboard
install -d -o root -g root -m 755 /opt/taskboard /opt/taskboard/bin /opt/taskboard/web
install -d -o root -g taskboard -m 750 /opt/taskboard/app
install -d -o root -g taskboard -m 750 /etc/taskboard
install -o root -g root -m 755 start.sh /opt/taskboard/bin/start.sh
install -o root -g root -m 755 create-app-db-user.sh /opt/taskboard/bin/create-app-db-user.sh

# Spring Boot の設定（パスワードは入れない。start.sh がパラメータストアから読む）
cat > /etc/taskboard/taskboard.env <<'ENV'
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://${DB_ENDPOINT}:5432/task_app?sslmode=require
DB_USERNAME=task_app_user
DB_PASSWORD_PARAMETER=${PARAMETER_NAME}
AWS_REGION=${REGION}
JAVA_OPTS="-Xms128m -Xmx384m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC"
ENV
chown root:taskboard /etc/taskboard/taskboard.env
chmod 640 /etc/taskboard/taskboard.env

# Spring Boot の起動の設定（jar がまだなければ、起動はしない）
install -o root -g root -m 644 taskboard.service /etc/systemd/system/taskboard.service
systemctl daemon-reload
systemctl enable taskboard >/dev/null 2>&1

# Nginx（元の設定は1回だけ残しておく）
[ -f /etc/nginx/nginx.conf.orig ] || cp /etc/nginx/nginx.conf /etc/nginx/nginx.conf.orig
install -o root -g root -m 644 nginx.conf /etc/nginx/nginx.conf
install -o root -g root -m 644 taskboard-headers.conf /etc/nginx/taskboard-headers.conf
nginx -t -q
systemctl reload nginx

rm -rf /tmp/taskboard-setup
echo "EC2 の準備ができました"
EOF
