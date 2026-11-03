# =============================================================
# 変数（入れ替えられる値）
# 値は terraform.tfvars に書く（Git には入れない）。
# 書き方の見本は terraform.tfvars.example。
# =============================================================

variable "project_name" {
  description = "名前の頭に付ける文字（AWS の画面で見分けるため）"
  type        = string
  default     = "taskboard"
}

variable "region" {
  description = "AWS のリージョン"
  type        = string
  default     = "ap-northeast-1" # 東京
}

variable "my_ip_cidr" {
  description = "SSH（22）とアプリ（8080）を許す、自分のパソコンの IP アドレス（例：203.0.113.10/32）"
  type        = string

  validation {
    condition     = can(cidrhost(var.my_ip_cidr, 0)) && endswith(var.my_ip_cidr, "/32")
    error_message = "my_ip_cidr は「IP アドレス/32」の形で、1つの IP アドレスだけを書いてください（0.0.0.0/0 のような広い範囲は不可）。"
  }
}

variable "ssh_public_key_path" {
  description = "EC2 に登録する SSH の公開鍵のファイル（秘密鍵ではなく .pub のほう）"
  type        = string
}

variable "instance_type" {
  description = "EC2 の大きさ"
  type        = string
  default     = "t4g.micro" # 2コア・メモリ 1GB（ARM）。段階2で DB を EC2 に入れるなら t4g.small に上げる
}
