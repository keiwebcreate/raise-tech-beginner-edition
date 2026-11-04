#!/bin/bash
# Spring Boot を起動する（taskboard.service から呼ばれる。/opt/taskboard/bin/start.sh に置く）
# DB のパスワードを、EC2 の権限（IAM ロール）でパラメータストアから読み、環境変数で Spring Boot に渡す。
# パスワードはファイルに書かない（要件 NS-12）
set -euo pipefail

DB_PASSWORD="$(aws ssm get-parameter \
  --region "$AWS_REGION" \
  --name "$DB_PASSWORD_PARAMETER" \
  --with-decryption \
  --query Parameter.Value \
  --output text)"
export DB_PASSWORD

# JAVA_OPTS は複数の指定をスペースで区切って並べたものなので、わざと "" で囲まない
# shellcheck disable=SC2086
exec java $JAVA_OPTS -jar /opt/taskboard/app/taskboard.jar
