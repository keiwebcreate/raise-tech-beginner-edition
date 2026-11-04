# =============================================================
# 出力：apply のあとに表示する値（terraform output でも見られる）
# =============================================================

output "ec2_public_ip" {
  description = "EC2 の外から見える IP アドレス（止めて起動し直すと変わる）"
  value       = aws_instance.app.public_ip
}

output "ec2_public_dns" {
  description = "EC2 の外から見える名前"
  value       = aws_instance.app.public_dns
}

output "ssh_command" {
  description = "EC2 にログインするコマンド（秘密鍵の場所は自分の環境に合わせる）"
  value       = "ssh -i ~/.ssh/taskboard_ec2 ec2-user@${aws_instance.app.public_ip}"
}

output "db_endpoint" {
  description = "RDS の住所（VPC の中からだけ使える）"
  value       = aws_db_instance.main.address
}

output "db_master_secret_arn" {
  description = "RDS の管理者のパスワードを預けた Secrets Manager の場所（パスワードそのものではない）"
  value       = aws_db_instance.main.master_user_secret[0].secret_arn
}

output "psql_command" {
  description = "EC2 の中で RDS につなぐコマンド（パスワードは聞かれたときに入れる）"
  value       = "psql \"host=${aws_db_instance.main.address} port=5432 dbname=task_app user=dbadmin sslmode=require\""
}

output "region" {
  description = "AWS のリージョン（scripts/setup-server.sh が使う）"
  value       = var.region
}

output "app_db_password_parameter" {
  description = "アプリ用の DB パスワードを入れたパラメータの名前（値そのものではない）"
  value       = aws_ssm_parameter.app_db_password.name
}

output "cloudfront_url" {
  description = "公開の URL（HTTPS。段階4）"
  value       = "https://${aws_cloudfront_distribution.app.domain_name}"
}

output "app_url" {
  description = "ブラウザで開く URL（段階1では Nginx の初期のページ。段階3からアプリが出る）"
  value       = "http://${aws_instance.app.public_ip}"
}
