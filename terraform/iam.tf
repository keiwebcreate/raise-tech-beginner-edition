# =============================================================
# EC2 に付ける AWS の権限（IAM ロール）（段階3）
# できるのは「アプリ用の DB パスワードのパラメータを読む」ことだけ。
# Spring Boot を起動する前に、EC2 がこの権限でパスワードを読む（deploy/ec2/start.sh）。
# =============================================================

# 「EC2 がこのロールを使ってよい」という決まり
data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "app" {
  name               = "${var.project_name}-app-ec2"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

# ロールにできること：1つのパラメータを読むだけ
# （暗号化の鍵は AWS が管理する aws/ssm なので、パラメータストア経由の復号は鍵の側で許されている）
data "aws_iam_policy_document" "read_app_db_password" {
  statement {
    actions   = ["ssm:GetParameter"]
    resources = [aws_ssm_parameter.app_db_password.arn]
  }
}

resource "aws_iam_role_policy" "read_app_db_password" {
  name   = "read-app-db-password"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.read_app_db_password.json
}

# EC2 にロールを付けるための入れ物
resource "aws_iam_instance_profile" "app" {
  name = "${var.project_name}-app-ec2"
  role = aws_iam_role.app.name
}
