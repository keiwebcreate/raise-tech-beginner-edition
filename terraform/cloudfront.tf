# =============================================================
# CloudFront（段階4）
# EC2 の前に置き、https://xxxx.cloudfront.net（無料の住所と証明書）で配る（要件 NS-02）。
#
#   ブラウザ ─HTTPS─▶ CloudFront ─HTTP─▶ EC2 の Nginx（80）
#
# CloudFront と EC2 の間は HTTP のまま（ドメインを持っていないので、EC2 に証明書を付けられない。
# 2026-11-04 にユーザーと決めた。docs/deployment.md 8章）。
# EC2 の 80 番には CloudFront からしか届かない（security_groups.tf）。
# =============================================================

# AWS が用意している決まり（キャッシュのしかた・EC2 へ渡すもの）
data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

# ブラウザから来たヘッダー・Cookie・クエリをすべて EC2 へ渡し、
# CloudFront のヘッダー（CloudFront-Forwarded-Proto：元は https だったか）も足す
data "aws_cloudfront_origin_request_policy" "all_viewer_and_cloudfront" {
  name = "Managed-AllViewerAndCloudFrontHeaders-2022-06"
}

# すべてのレスポンスに HSTS（このサイトは常に HTTPS で開く、とブラウザに覚えさせる）を付ける（要件 NS-10）
# ほかのセキュリティのヘッダーは、画面は Nginx（taskboard-headers.conf）、API は Spring Security が付ける
resource "aws_cloudfront_response_headers_policy" "security" {
  name    = "${var.project_name}-security-headers"
  comment = "TaskBoard: HSTS"

  security_headers_config {
    strict_transport_security {
      access_control_max_age_sec = 31536000 # 1年
      include_subdomains         = false
      preload                    = false
      override                   = true
    }
  }
}

resource "aws_cloudfront_distribution" "app" {
  enabled      = true
  comment      = "TaskBoard"
  http_version = "http2and3"
  # IPv6 は使わない。IPv6 は1人（1つの回線）が非常に多くのアドレスを持てるので、
  # アドレスを変えながら試されると、IP アドレスごとの回数制限（要件 U-10）が効かないため（詳細設計書 3.8.8）。
  # ブラウザは自動で IPv4 でつなぐので、使う人には違いがない（2026-11-04 にユーザーと決めた）
  is_ipv6_enabled = false
  price_class     = "PriceClass_200" # 日本とオーストラリアを含む地域（いちばん安い 100 は北米・欧州だけ）

  origin {
    origin_id   = "ec2"
    domain_name = aws_instance.app.public_dns # EC2 の名前（止めて起動し直すと変わる。そのときは apply し直す）

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only" # EC2 までは HTTP（上の説明）
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  # 画面（/assets/ 以外）：キャッシュしない。index.html と /boards/1 などは、いつも EC2 から最新を返す
  default_cache_behavior {
    target_origin_id           = "ec2"
    viewer_protocol_policy     = "redirect-to-https" # http で来たら https に移す
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = data.aws_cloudfront_cache_policy.caching_disabled.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
  }

  # API：キャッシュしない。Cookie・ヘッダー（CSRF のトークンなど）・クエリをすべて EC2 へ渡す
  ordered_cache_behavior {
    path_pattern               = "/api/*"
    target_origin_id           = "ec2"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id   = data.aws_cloudfront_origin_request_policy.all_viewer_and_cloudfront.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
  }

  # 画面のファイルのうち、名前に中身の印（ハッシュ）が付いたもの：CloudFront でキャッシュする
  ordered_cache_behavior {
    path_pattern               = "/assets/*"
    target_origin_id           = "ec2"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = data.aws_cloudfront_cache_policy.caching_optimized.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # 証明書：CloudFront の無料の住所（*.cloudfront.net）のものを使う
  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = { Name = "${var.project_name}-cdn" }
}
