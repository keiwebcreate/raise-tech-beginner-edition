# =============================================================
# RDS（AWS が管理する PostgreSQL）（段階2）
# インターネットからは見えず、EC2 からだけつながる（要件 NS-14）。
# =============================================================

# RDS を置く区画の組（プライベートサブネット 2つ。network.tf）
resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnets"
  subnet_ids = [aws_subnet.private_a.id, aws_subnet.private_b.id]

  tags = { Name = "${var.project_name}-db-subnets" }
}

resource "aws_db_instance" "main" {
  identifier     = "${var.project_name}-db"
  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  # ディスク：gp3 の最小（20GB）。暗号化する
  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  # 最初に作る DB と、管理者のユーザー名
  db_name  = "task_app"
  username = "dbadmin"

  # 管理者のパスワードは AWS に作らせ、Secrets Manager（秘密の値の預け先）に入れる。
  # Terraform のファイルにも state にもパスワードは残らない（要件 NS-12）
  manage_master_user_password = true

  # 置き場所とファイアウォール
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  availability_zone      = "${var.region}a" # EC2 と同じデータセンター（別のデータセンターとの通信は料金がかかるため）
  multi_az               = false            # 1か所だけ（予備を別の場所に置くと料金が倍になる）
  publicly_accessible    = false            # インターネットから見える住所を付けない

  # 自動バックアップ：7日分（要件 NO-02）。DB の大きさまでのバックアップの保存は無料
  backup_retention_period = 7
  copy_tags_to_snapshot   = true

  # 消すとき：最後のスナップショット（DB の写し）は取らない。
  # デプロイを確かめたらすぐ消す前提で、写しを残すと料金がかかり続けるため
  skip_final_snapshot = true
  deletion_protection = false

  # 小さな更新（18.6 → 18.7 など）は AWS が自動で当てる。設定の変更はすぐ反映する
  auto_minor_version_upgrade = true
  apply_immediately          = true

  tags = { Name = "${var.project_name}-db" }
}
