# =============================================================
# Terraform と、AWS を操作する部品（プロバイダー）の設定
# =============================================================

terraform {
  # この版より古い Terraform では動かさない
  required_version = ">= 1.14"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0" # 6.x の中で最新を使う（7 には勝手に上げない）
    }
  }

  # state（何を作ったかの記録）は、今はこのフォルダの terraform.tfstate に置く。
  # 秘密の値が入るので Git には入れない（.gitignore）。
}

provider "aws" {
  region = var.region

  # このプロバイダーで作るものすべてに付ける札（タグ）。
  # コンソールや請求で「このアプリのもの」と見分けられる。
  default_tags {
    tags = {
      Project   = var.project_name
      ManagedBy = "terraform"
    }
  }
}
