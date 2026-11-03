# デプロイ手順書：トレロ風タスク管理アプリ

最終更新：2026-11-03（段階1：ネットワークと EC2。イシュー #65）

このアプリを AWS に公開する（要件 G-04）ための手順と、その考え方をまとめた文書。AWS や Terraform を初めて使う人が、この文書だけで同じ環境を作り直し・削除できることを目指す。

- 要件は `requirements.md`（G-04・NS-02・NS-12・NS-14・NO-01）、アプリの作りは `detailed-design.md` を参照
- AWS の画面（マネジメントコンソール）は、確認と、コマンドでできない初期設定にだけ使う。作るのはすべて **AWS CLI と Terraform** で行う（講座の進め方）

## 1. 用語

| 用語 | 意味 |
|---|---|
| AWS | Amazon が貸し出す、インターネット上のサーバー・DB・ファイル置き場などの部品の集まり。使った分だけ料金がかかる |
| リージョン | AWS のデータセンターがある地域。このアプリは東京（`ap-northeast-1`） |
| IAM ユーザー | AWS を操作する人やプログラムのアカウント。ルートユーザー（申し込んだメールアドレスのアカウント）とは別に作り、ふだんの作業はこちらで行う |
| アクセスキー | IAM ユーザーとしてプログラムから AWS を操作するための鍵（ID と秘密の文字列の組）。パスワードと同じく、人に見せない |
| AWS CLI | AWS をコマンドで操作する道具（例：`aws s3 ls`） |
| IaC（Infrastructure as Code） | サーバーやネットワークの構成を、画面で手作業で作る代わりに、ファイルに書いて作る考え方。同じものを何度でも作り直せ、Git で変更の履歴が残る |
| Terraform | IaC の道具。`.tf` ファイルに「欲しい形」を書くと、今の AWS と比べて、足りないものを作り、違うものを直す |
| state | Terraform が「何を作ったか」を覚えておく記録（`terraform.tfstate`）。秘密の値が入ることがあるので Git に入れない |
| jq | JSON から必要なところだけを取り出すコマンド。AWS CLI の結果（JSON）を読むときに使う |
| VPC | AWS の中に作る、自分専用のネットワーク |
| サブネット | VPC の中の区画。インターネットとやりとりできる「パブリック」と、できない「プライベート」がある |
| セキュリティグループ | EC2 などのファイアウォール。「どこから・どのポートへの通信を許すか」を書き、書いていない通信は通さない |
| EC2 | AWS の貸しサーバー |
| RDS | AWS が管理する DB（バックアップや更新を AWS に任せられる） |
| CloudFront | AWS の配信の仕組み。`https://xxxx.cloudfront.net` の住所と HTTPS の証明書を無料で使える |

## 2. 構成

### 2.1 段階

一度にすべてを作らず、段階ごとに作って動作を確かめる。1つの段階が1つのイシュー。

| 段階 | 作るもの | 確かめること | 状態 |
|---|---|---|---|
| 1 | VPC・パブリックサブネット・セキュリティグループ・EC2 | SSH でログインでき、Java 25 が入っている | 作成中（#65） |
| 2 | DB（RDS にするか、EC2 の中に入れるかは、この段階で決める） | EC2 から DB につながる | 未着手 |
| 3 | アプリ（Spring Boot が画面のファイルも配る）を EC2 に載せる | `http://（EC2 の IP アドレス）:8080` でログイン画面が出る | 未着手 |
| 4 | CloudFront を EC2 の前に置き、HTTPS にする | `https://xxxx.cloudfront.net` で使える（受け入れ基準 AC-44〜AC-54） | 未着手 |

### 2.2 段階1の構成

```
自分のパソコン（IP アドレスを1つだけ許す）
   │  SSH（22）・アプリ（8080）
   ▼
AWS 東京リージョン
└─ VPC（10.0.0.0/16）
    └─ パブリックサブネット（10.0.1.0/24・ap-northeast-1a）
        └─ EC2（t4g.micro・Amazon Linux 2023・Java 25）
```

### 2.3 決めたこと

| # | 項目 | 決定 | 理由 |
|---|---|---|---|
| 1 | 作り方 | AWS CLI と Terraform。画面での手作業はしない | 同じものを作り直せる。Git に履歴が残る。AI がファイルを書いて進められる（講座の進め方） |
| 2 | AWS CLI の認証 | IAM ユーザーのアクセスキー（`aws configure`） | 講座と同じ形で、手順が少ない。鍵の守り方は 6章 |
| 3 | 進め方 | 段階に分ける（2.1） | 問題が起きたとき、どこが原因かがわかりやすい（講座の進め方） |
| 4 | 画面の配り方 | Spring Boot が画面のファイルも配り、同じサーバー・同じポートで出す（S3 は使わない） | いちばん単純。画面と API が同じ住所になり、CORS の設定がいらない（詳細設計書の方針どおり） |
| 5 | HTTPS | 段階4で CloudFront の無料の住所（`*.cloudfront.net`）を使う。ドメインは買わない | ログインがあるので HTTPS は必須（NS-02）。ドメインの購入はクレジットで払えないことがある |
| 6 | EC2 へのログイン | SSH（鍵ファイル）。ポート 22 は自分の IP アドレスだけに許す | 講座と同じ方法。ファイルの送り込み（`scp`）も簡単 |
| 7 | EC2 の大きさ | t4g.micro（ARM・メモリ 1GB）。段階2で DB を EC2 に入れるなら t4g.small（2GB）に上げる | 無料プランのクレジットで長くもつ。Java は ARM でもそのまま動く |
| 8 | state の置き場所 | パソコンの `terraform/terraform.tfstate`（Git には入れない） | 作業するのが1人なので、共有の置き場所（S3 など）はまだいらない |

## 3. 料金とクレジット

### 3.1 無料プラン

このアカウントは AWS の**無料プラン**（2026-11-03 に作成）。

- 使った料金は**クレジット**から引かれる（最初 100 USD。下の操作をすると 1つにつき 20 USD 増える）。無料プランのあいだは、クレジットを使い切っても請求は来ない
- 期限は **2027-05-03**。クレジットを使い切るか期限が来ると、有料プランに切り替えない限りアカウントが止まり、作ったものは消える（要件 NO-01 の「無料期間が終わったら、公開をやめるか構成を見直す」）
- クレジットが増える操作：予算を作る（済み）、EC2 を起動する、RDS で DB を作る、Lambda で Web アプリを作る、Bedrock を試す

残りのクレジットと期限は、次のコマンドで見られる。

```bash
aws freetier get-account-plan-state
aws freetier list-account-activities --query "activities[].[title,status]" --output text
```

### 3.2 料金の目安（東京・2026-11-03 時点の公開価格）

| もの | 料金 | ひと月（730時間） |
|---|---|---|
| EC2 t4g.micro | 0.0108 USD/時 | 約 7.9 USD |
| EC2 t4g.small | 0.0216 USD/時 | 約 15.8 USD |
| 外から見える IP アドレス（IPv4） | 0.005 USD/時 | 約 3.7 USD |
| EC2 のディスク（gp3・20GB） | | 約 1.9 USD |
| RDS db.t4g.micro（PostgreSQL・1か所） | 0.025 USD/時 | 約 18.3 USD（＋ディスク） |
| CloudFront | 月 1TB の転送まで無料 | 0 USD |

段階1の構成は**ひと月 約 13.5 USD（1日 約 0.45 USD）**。使わない時期は `terraform destroy` で消せば料金はかからない（5.6）。

### 3.3 予算のアラート

AWS Budgets に「ゼロ支出予算」（料金が 0.01 USD を超えたらメール）を作ってある（要件 NO-01・受け入れ基準 AC-56）。料金がかかり始めたら、月の予算額で知らせる形に変えるかを検討する。

## 4. 準備（最初に1回だけ）

### 4.1 道具を入れる

PowerShell で実行する。終わったら VS Code（Cursor）を開き直す（新しいコマンドを読み込むため）。

```powershell
winget install --id Amazon.AWSCLI -e
winget install --id Hashicorp.Terraform -e
winget install --id jqlang.jq -e
```

確かめる：

```powershell
aws --version
terraform version
jq --version
```

### 4.2 AWS アカウントの安全の設定（コンソールで行う）

1. **ルートユーザーに MFA を付ける**：右上のアカウント名 →「セキュリティ認証情報」→「多要素認証 (MFA)」
2. **予算のアラートを作る**：https://console.aws.amazon.com/costmanagement/home#/budgets →「予算を作成」→ テンプレートから選ぶ
3. **IAM ユーザーを作る**：IAM →「ユーザー」→「ユーザーの作成」。権限は `AdministratorAccess`（Terraform でいろいろなものを作るため）。コンソールへのアクセスはなし（プログラムからだけ使う）

### 4.3 アクセスキーを作り、AWS CLI に設定する

1. IAM →「ユーザー」→ 作ったユーザー →「セキュリティ認証情報」→「アクセスキーを作成」→ ユースケースは「コマンドラインインターフェイス (CLI)」
2. 表示されたシークレットアクセスキーは、この画面でしか見られない。CSV をダウンロードする場合は、**プロジェクトのフォルダの外**に置く
3. PowerShell で設定する（チャットや Git に鍵を書かない）

```powershell
aws configure
# AWS Access Key ID     : （アクセスキー）
# AWS Secret Access Key : （シークレットアクセスキー）
# Default region name   : ap-northeast-1
# Default output format : json
```

設定は `C:\Users\（ユーザー名）\.aws\` に保存される。途中で「AWS のスキルと MCP サーバーを AI の道具に設定するか」と聞かれたら `n` でよい。

確かめる（誰として AWS につながっているかを表示するだけで、何も変えない）：

```bash
aws sts get-caller-identity
```

## 5. 段階1：ネットワークと EC2

### 5.1 ファイル

すべて `terraform/` にある。

| ファイル | 中身 |
|---|---|
| `versions.tf` | Terraform と AWS プロバイダー（AWS とやりとりする部品）の版、リージョン、全部に付ける札（タグ） |
| `variables.tf` | 変数（入れ替えられる値）の定義 |
| `terraform.tfvars.example` | 変数の値の見本。コピーして `terraform.tfvars` を作る |
| `terraform.tfvars` | 変数の値（自分の IP アドレスなど）。**Git に入れない** |
| `network.tf` | VPC・インターネットゲートウェイ・パブリックサブネット・ルートテーブル |
| `security_groups.tf` | EC2 のセキュリティグループ（22 と 8080 を自分の IP アドレスだけに許す） |
| `ec2.tf` | SSH の公開鍵の登録と EC2。起動時に Java 25 を入れる |
| `outputs.tf` | 作ったあとに表示する値（IP アドレス・SSH のコマンドなど） |
| `.terraform.lock.hcl` | プロバイダーの版を固定する記録。Git に入れる |
| `.gitignore` | state・`terraform.tfvars`・鍵などを Git に入れないための設定 |

### 5.2 SSH の鍵を作る

パソコンで鍵の組（秘密鍵と公開鍵）を作る。AWS に渡すのは公開鍵（`.pub`）だけ。

```bash
ssh-keygen -t ed25519 -f ~/.ssh/taskboard_ec2 -C "taskboard-ec2"
```

- `~/.ssh/taskboard_ec2`（秘密鍵）は人に渡さない。プロジェクトのフォルダに置かない
- パスフレーズ（鍵のパスワード）は付けたほうが安全。付けると、使うたびに聞かれる

### 5.3 変数の値を書く

```bash
cd terraform
cp terraform.tfvars.example terraform.tfvars
curl -s https://checkip.amazonaws.com   # 自分の IP アドレスを調べる
```

`terraform.tfvars` の `my_ip_cidr` を「調べた IP アドレス/32」に書き換える。家の IP アドレスが変わったら（SSH がつながらなくなったら）、ここを直して 5.4 の `plan`・`apply` をやり直す。

### 5.4 作る

```bash
cd terraform
terraform init       # 準備（プロバイダーを取ってくる）。初回と、プロバイダーを変えたときだけ
terraform fmt        # 書き方をそろえる
terraform validate   # 書き方に誤りがないか確かめる
terraform plan       # 何を作る・変える・消すかの予定を表示する（まだ何もしない）
terraform apply      # 予定どおりに作る（料金が発生する）
```

- **`apply` の前に、必ず `plan` の結果を読む**。AI が作業するときは、AI が `plan` の結果を説明し、ユーザーが OK と言ってから `apply` する
- 段階1の `plan` は「11 to add」（VPC・インターネットゲートウェイ・サブネット・ルートテーブル・その結びつけ・セキュリティグループ・ルール3つ・キーペア・EC2）になる

### 5.5 確かめる

```bash
terraform output                     # IP アドレスと SSH のコマンドが出る
ssh -i ~/.ssh/taskboard_ec2 ec2-user@$(terraform output -raw ec2_public_ip)
```

EC2 の中で：

```bash
java -version    # 25 が出る（起動から数分かかることがある。出なければ少し待つ）
exit
```

セキュリティグループが自分の IP アドレスだけを許しているか：

```bash
aws ec2 describe-security-group-rules \
  --filters Name=group-id,Values=$(aws ec2 describe-security-groups --filters Name=group-name,Values=taskboard-ec2-sg --query "SecurityGroups[0].GroupId" --output text) \
  --query "SecurityGroupRules[?!IsEgress].[FromPort,CidrIpv4]" --output text
# 22 と 8080 の行に、自分の IP アドレス/32 だけが出る
```

### 5.6 削除する

```bash
cd terraform
terraform plan -destroy   # 何が消えるかを確かめる
terraform destroy         # すべて消す（料金がかからなくなる）
```

消したあとも、`terraform apply` でいつでも同じものを作り直せる（IP アドレスは変わる）。

## 6. 安全のための決まり

- **秘密の値（アクセスキー・SSH の秘密鍵・DB のパスワード）は、チャット・Git・プロジェクトのフォルダに出さない**（要件 NS-12）
- アクセスキーは期限がないので、長く使わない時期は IAM の画面で「無効」にする。漏れたかもしれないときは、すぐ削除して作り直す
- ルートユーザーのアクセスキーは作らない。ルートユーザーはコンソールで、MFA を付けて使う
- 外からの入口は、必要なポートを、必要な相手にだけ開ける（`0.0.0.0/0` で開けない。`variables.tf` の入力チェックで `/32` 以外を断る）
- Terraform で作ったものを、コンソールで手で直さない（ファイルと実物がずれる）
- `terraform apply`・`destroy` は、`plan` の結果を確かめてから

## 7. 困ったとき

| 症状 | 原因と対処 |
|---|---|
| `aws` や `terraform` が見つからない | 入れたあとで VS Code（Cursor）を開き直していない |
| `explicit deny in a service control policy` で断られる | 作ったばかりのアカウントでは、AWS の確認が終わるまで（数時間〜24時間）、IAM ユーザーから EC2・S3 などを使えないことがある（2026-11-03 に発生）。IAM の権限を足しても解決しない（SCP は IAM より上の段の決まり）。24時間たっても変わらなければ、AWS サポート（アカウントと請求。無料）に問い合わせる |
| SSH がつながらない（`Connection timed out`） | 家の IP アドレスが変わった。5.3 で `my_ip_cidr` を直し、`plan`・`apply` する |
| `java -version` が見つからない | 起動直後で、Java をまだ入れている途中。数分待つ。`sudo cat /var/log/cloud-init-output.log` で進み具合を見られる |
| Git Bash で `aws ssm …` に渡したパスが `C:/Program Files/Git/…` に化ける | Git Bash がパスを Windows の形に直している。前に `MSYS_NO_PATHCONV=1` を付ける |

## 8. 改訂履歴

| 版 | 日付 | 内容 |
|---|---|---|
| 1.0 | 2026-11-03 | 新規作成。用語、構成と段階、料金とクレジット、準備、段階1（ネットワークと EC2）（#65） |
