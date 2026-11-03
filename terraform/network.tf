# =============================================================
# ネットワーク
# VPC（AWS の中の自分専用のネットワーク）に、インターネットとつながる
# サブネット（区画）を1つ作る。
# 段階2で RDS を使うことにしたら、DB 用の区画（インターネットに出ない）を足す。
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
  availability_zone       = "${var.region}a" # 東京の「a」のデータセンター
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
