# =============================================================
# セキュリティグループ（EC2 のファイアウォール）
# 「どこから・どのポートへの通信を許すか」を決める。書いていない通信は通さない。
# =============================================================

resource "aws_security_group" "ec2" {
  name = "${var.project_name}-ec2-sg"
  # 説明文は段階1のときのまま（今の HTTP は CloudFront から）。説明文を変えると、セキュリティグループが作り直しになるため
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

# 入ってくる通信 2：HTTP（80）。CloudFront からだけ（段階4。段階1〜3は自分の IP アドレスからだった）。
# Nginx が受け、画面のファイルを返し、/api は Spring Boot（8080）へ転送する。
# Spring Boot の 8080 は外に開けない（同じ EC2 の中の Nginx からだけ使う）。
# AWS が用意している「CloudFront が EC2 へつなぐときに使うアドレスの一覧」を相手にする
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_vpc_security_group_ingress_rule" "http_from_cloudfront" {
  security_group_id = aws_security_group.ec2.id
  description       = "HTTP (Nginx) from CloudFront"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront.id
}

# 出ていく通信：すべて許す（Java などを取ってくるため、RDS へつなぐため）
resource "aws_vpc_security_group_egress_rule" "all_out" {
  security_group_id = aws_security_group.ec2.id
  description       = "All outbound"
  ip_protocol       = "-1" # すべての種類
  cidr_ipv4         = "0.0.0.0/0"
}

# =============================================================
# RDS のセキュリティグループ（段階2）
# PostgreSQL（5432）を、EC2 のセキュリティグループが付いたものからだけ許す（要件 NS-14）。
# IP アドレスではなく「EC2 のグループ」を相手にするので、EC2 の IP アドレスが変わっても直さなくてよい。
# =============================================================

resource "aws_security_group" "db" {
  name        = "${var.project_name}-db-sg"
  description = "TaskBoard RDS: PostgreSQL from the app EC2 only"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.project_name}-db-sg" }
}

resource "aws_vpc_security_group_ingress_rule" "db_from_ec2" {
  security_group_id            = aws_security_group.db.id
  description                  = "PostgreSQL from the app EC2"
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  referenced_security_group_id = aws_security_group.ec2.id
}

# RDS から外へ出ていく通信のルールは書かない（DB から通信を始める必要がないため。書かなければ通さない）
