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

output "app_url" {
  description = "段階3でアプリを載せたあとの URL（今はまだ何も動いていない）"
  value       = "http://${aws_instance.app.public_ip}:8080"
}
