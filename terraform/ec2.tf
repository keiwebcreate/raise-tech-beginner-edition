# =============================================================
# EC2（サーバー）
# =============================================================

# Amazon Linux 2023（ARM 用）の最新のイメージ（OS の型紙）の ID を、AWS が公開している場所から読む
data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

# SSH の公開鍵を AWS に登録する（秘密鍵はパソコンにだけ置く）
resource "aws_key_pair" "main" {
  key_name   = "${var.project_name}-key"
  public_key = file(pathexpand(var.ssh_public_key_path))
}

resource "aws_instance" "app" {
  ami                    = data.aws_ssm_parameter.al2023_arm64.value
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public_a.id
  vpc_security_group_ids = [aws_security_group.ec2.id]
  key_name               = aws_key_pair.main.key_name

  # EC2 に付ける AWS の権限（パラメータストアの DB パスワードを読むだけ。iam.tf）。
  # 付けても外しても、EC2 は起動し直さない
  iam_instance_profile = aws_iam_instance_profile.app.name

  # サーバーの中から AWS の情報を読む仕組み（メタデータ）は、安全な新しい方式（IMDSv2）だけにする
  metadata_options {
    http_tokens   = "required"
    http_endpoint = "enabled"
  }

  # ディスク：暗号化する。20GB（Java・アプリ・ログ。段階2で DB を入れても足りる大きさ）
  root_block_device {
    volume_type           = "gp3"
    volume_size           = 20
    encrypted             = true
    delete_on_termination = true
  }

  # 初めて起動したときに1回だけ動く命令
  # - Java 25：Spring Boot を動かす
  # - Nginx：画面のファイルを配り、/api を Spring Boot へ転送する（転送の設定は段階3）
  # 画面はパソコンで npm run build してから送るので、Node.js は入れない
  user_data = <<-EOF
    #!/bin/bash
    set -eu
    dnf -y update
    dnf -y install java-25-amazon-corretto-headless nginx
    systemctl enable --now nginx
  EOF

  # イメージの最新版が出るたびにサーバーを作り直さないよう、作ったあとの AMI の変化は無視する
  lifecycle {
    ignore_changes = [ami]
  }

  tags = { Name = "${var.project_name}-app" }
}
