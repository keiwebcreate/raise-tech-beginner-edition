# =============================================================
# アプリ用の DB パスワード（段階3）
# SSM パラメータストア（AWS のパラメータ管理機能）に暗号化して入れる（要件 NS-12）。
#
# パスワードは Terraform が作るが、state にもファイルにも残さない：
# - ephemeral（その場限りの値）：plan・apply の間だけ作られ、state に書かれない
# - value_wo（書き込むだけの項目）：AWS に送るだけで、state に書かれない
# パスワードを作り直したいときは、value_wo_version の数字を 1 つ増やして apply する
# （そのあと、EC2 で DB ユーザーのパスワードも合わせ直す。deployment.md 7章）
# =============================================================

ephemeral "random_password" "app_db" {
  length  = 32
  special = false # 英数字だけ（接続文字列やコマンドで記号の扱いに困らないように。32文字で十分に強い）
}

resource "aws_ssm_parameter" "app_db_password" {
  name        = "/${var.project_name}/db/app-password"
  description = "TaskBoard: password of the app DB user (task_app_user)"
  type        = "SecureString" # AWS が管理する鍵（aws/ssm）で暗号化して保存する

  value_wo         = ephemeral.random_password.app_db.result
  value_wo_version = 1
}
