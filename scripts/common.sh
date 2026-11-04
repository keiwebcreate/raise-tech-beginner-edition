#!/bin/bash
# scripts/ のスクリプトが共通で使う設定（直接は実行しない。source で読み込む）
# Git Bash（Windows）で動かす前提。

set -euo pipefail

# beginner_version フォルダ（このファイルの1つ上）
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF_DIR="$(cygpath -w "$ROOT_DIR/terraform")" # terraform は Windows のプログラムなので、Windows の形のパスで渡す

# SSH と scp は Windows の OpenSSH を使う。
# Windows の ssh-agent に預けた鍵を使うため（Git Bash の ssh は、Windows の ssh-agent を使えない）
if [ -x /c/Windows/System32/OpenSSH/ssh.exe ]; then
  SSH=/c/Windows/System32/OpenSSH/ssh.exe
  SCP=/c/Windows/System32/OpenSSH/scp.exe
else
  SSH=ssh
  SCP=scp
fi

# Windows の scp に Git Bash のパス（/c/dev/…）を渡すと、Git Bash が勝手に書き換えることがある。
# 書き換えを止め、パソコン側のパスは自分で Windows の形（C:\dev\…）に直して渡す
export MSYS_NO_PATHCONV=1
to_win_path() { cygpath -w "$1"; }

# EC2 の住所は Terraform の出力から読む（EC2 を作り直して IP アドレスが変わっても、直さなくてよい）
EC2_HOST="$(terraform -chdir="$TF_DIR" output -raw ec2_public_ip)"
EC2_TARGET="ec2-user@${EC2_HOST}"
SSH_OPTS=(-o StrictHostKeyChecking=accept-new -o ConnectTimeout=15)

remote() { "$SSH" "${SSH_OPTS[@]}" "$EC2_TARGET" "$@"; }
send() { "$SCP" "${SSH_OPTS[@]}" -q "$(to_win_path "$1")" "${EC2_TARGET}:$2"; }
