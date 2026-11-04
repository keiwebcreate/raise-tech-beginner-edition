# =============================================================
# ネットワーク
# VPC（AWS の中の自分専用のネットワーク）に、次の区画（サブネット）を作る。
# - パブリック 1つ：EC2 を置く。インターネットとやりとりできる
# - プライベート 2つ：RDS を置く。インターネットとはやりとりできない（段階2）
# =============================================================

resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16" # 10.0.0.0〜10.0.255.255 を使う
  enable_dns_support   = true
  enable_dns_hostnames = true # EC2 に「ec2-…amazonaws.com」の名前を付ける

  tags = { Name = "${var.project_name}-vpc" }
}

# インターネットとの出入口
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${var.project_name}-igw" }
}

# パブリックサブネット：ここに置いたものは、インターネットとやりとりできる
resource "aws_subnet" "public_a" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = "${var.region}a" # リージョンの「a」のデータセンター（シドニーなら ap-southeast-2a。t4g.micro が使えることを確認済み）
  map_public_ip_on_launch = true             # ここで作る EC2 に、外から見える IP アドレスを付ける

  tags = { Name = "${var.project_name}-public-a" }
}

# 道案内の表（ルートテーブル）：VPC の外あての通信は、すべて出入口へ
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${var.project_name}-public-rt" }
}

resource "aws_route_table_association" "public_a" {
  subnet_id      = aws_subnet.public_a.id
  route_table_id = aws_route_table.public.id
}

# プライベートサブネット：RDS を置く。
# RDS は「2か所以上のデータセンターにまたがる区画の組」が必要なので、a と b に1つずつ作る
# （DB 自体は a の1か所だけで動かす。rds.tf）。
resource "aws_subnet" "private_a" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.11.0/24"
  availability_zone = "${var.region}a"

  tags = { Name = "${var.project_name}-private-a" }
}

resource "aws_subnet" "private_b" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.12.0/24"
  availability_zone = "${var.region}b"

  tags = { Name = "${var.project_name}-private-b" }
}

# プライベート用の道案内の表：VPC の中あて（自動で入る）だけで、外への道はない。
# NAT ゲートウェイ（プライベートからインターネットへ出る道。月 約 45 USD）は使わない
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${var.project_name}-private-rt" }
}

resource "aws_route_table_association" "private_a" {
  subnet_id      = aws_subnet.private_a.id
  route_table_id = aws_route_table.private.id
}

resource "aws_route_table_association" "private_b" {
  subnet_id      = aws_subnet.private_b.id
  route_table_id = aws_route_table.private.id
}
