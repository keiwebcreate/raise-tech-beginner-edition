#!/bin/bash
# アプリ用の DB ユーザー（task_app_user）を RDS に作る（パスワードを合わせ直すときも、これをもう一度実行する）
# EC2 で、ユーザーが1回実行する（docs/deployment.md 7章）：
#   ssh -t ec2-user@（EC2 の IP アドレス） sudo /opt/taskboard/bin/create-app-db-user.sh
# - アプリ用のパスワードは、パラメータストアから読んで psql に渡す（画面には出さない）
# - 聞かれるのは、RDS の管理者（dbadmin）のパスワードだけ（Secrets Manager から取り出して貼る）
# - 権限は、task_app の DB につなぐことと、public スキーマにテーブルを作って使うことだけ。
#   管理者の権限（スーパーユーザー・DB やユーザーを作る権限）は持たせない（要件 NS-14）
set -euo pipefail

# shellcheck disable=SC1091
source /etc/taskboard/taskboard.env

# DB_URL（jdbc:postgresql://住所:5432/task_app?…）から、RDS の住所を取り出す
DB_HOST="$(echo "$DB_URL" | sed -E 's#^jdbc:postgresql://([^:/]+).*#\1#')"

APP_PASSWORD="$(aws ssm get-parameter \
  --region "$AWS_REGION" \
  --name "$DB_PASSWORD_PARAMETER" \
  --with-decryption \
  --query Parameter.Value \
  --output text)"

echo "RDS（${DB_HOST}）に、管理者 dbadmin でつなぎます。"

# パスワードは psql の変数（app_password）で渡し、SQL の中では :'app_password' で使う（文字列として正しく囲まれる）
{
  printf '\\set app_password %s\n' "$APP_PASSWORD"
  cat <<'SQL'
SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'task_app_user') AS role_exists \gset
\if :role_exists
ALTER ROLE task_app_user WITH LOGIN PASSWORD :'app_password';
\echo 'task_app_user のパスワードを、パラメータストアの値に合わせ直しました。'
\else
CREATE ROLE task_app_user WITH LOGIN PASSWORD :'app_password';
\echo 'task_app_user を作りました。'
\endif
-- ほかのユーザーが task_app の DB につながらないようにして、task_app_user にだけ許す
REVOKE ALL ON DATABASE task_app FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE task_app TO task_app_user;
-- テーブルは、アプリの起動時に Flyway が task_app_user として作る
GRANT USAGE, CREATE ON SCHEMA public TO task_app_user;
-- 確かめる：スーパーユーザー・DB を作る権限・ユーザーを作る権限が、すべて f（なし）であること
SELECT rolname, rolsuper, rolcreatedb, rolcreaterole FROM pg_roles WHERE rolname = 'task_app_user';
SQL
} | psql "host=${DB_HOST} port=5432 dbname=task_app user=dbadmin sslmode=require" -v ON_ERROR_STOP=1 --quiet
