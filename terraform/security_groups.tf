# =============================================================
# セキュリティグループ（EC2 のファイアウォール）
# 「どこから・どのポートへの通信を許すか」を決める。書いていない通信は通さない。
# =============================================================

resource "aws_security_group" "ec2" {
  name        = "${var.project_name}-ec2-sg"
  description = "TaskBoard EC2: SSH and HTTP from my IP only"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.project_name}-ec2-sg" }
}

# 入ってくる通信 1：SSH（ログイン・ファイルの送り込み）。自分の IP アドレスからだけ
resource "aws_vpc_security_group_ingress_rule" "ssh_from_my_ip" {
  security_group_id = aws_security_group.ec2.id
  description       = "SSH from my PC"
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
  cidr_ipv4         = var.my_ip_cidr
}

# 入ってくる通信 2：HTTP（80）。Nginx が受け、画面のファイルを返し、/api は Spring Boot（8080）へ転送する。
# Spring Boot の 8080 は外に開けない（同じ EC2 の中の Nginx からだけ使う）。
# 自分の IP アドレスからだけ。段階4で、CloudFront からだけに変える。
resource "aws_vpc_security_group_ingress_rule" "http_from_my_ip" {
  security_group_id = aws_security_group.ec2.id
  description       = "HTTP (Nginx) from my PC"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  cidr_ipv4         = var.my_ip_cidr
}

# 出ていく通信：すべて許す（Java などを取ってくるため）
resource "aws_vpc_security_group_egress_rule" "all_out" {
  security_group_id = aws_security_group.ec2.id
  description       = "All outbound"
  ip_protocol       = "-1" # すべての種類
  cidr_ipv4         = "0.0.0.0/0"
}
