#!/bin/bash
# 本番の DB（RDS）を、アプリ用のユーザー task_app_user で psql で開く（確認用）
# EC2 で、ユーザーが実行する（docs/deployment.md 7.5）：
#   ssh -t ec2-user@（EC2 の IP アドレス） sudo /opt/taskboard/bin/psql-app.sh
#   ssh -t ec2-user@（EC2 の IP アドレス） sudo /opt/taskboard/bin/psql-app.sh -c "select count(*) from users"
# パスワードは、パラメータストアから読んで psql に渡す（画面にもファイルにも出さない）
set -euo pipefail

# shellcheck disable=SC1091
source /etc/taskboard/taskboard.env

DB_HOST="$(echo "$DB_URL" | sed -E 's#^jdbc:postgresql://([^:/]+).*#\1#')"

PGPASSWORD="$(aws ssm get-parameter \
  --region "$AWS_REGION" \
  --name "$DB_PASSWORD_PARAMETER" \
  --with-decryption \
  --query Parameter.Value \
  --output text)"
export PGPASSWORD

exec psql "host=${DB_HOST} port=5432 dbname=task_app user=${DB_USERNAME} sslmode=require" "$@"
