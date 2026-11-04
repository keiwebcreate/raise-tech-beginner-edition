# デプロイ手順書：トレロ風タスク管理アプリ

最終更新：2026-11-04（段階3：アプリを載せた。イシュー #69）

このアプリを AWS に公開する（要件 G-04）ための手順と、その考え方をまとめた文書。AWS や Terraform を初めて使う人が、この文書だけで同じ環境を作り直し・削除できることを目指す。

- 要件は `requirements.md`（G-04・NS-02・NS-12・NS-14・NO-01）、アプリの作りは `detailed-design.md` を参照
- AWS の画面（マネジメントコンソール）は、確認と、コマンドでできない初期設定にだけ使う。作るのはすべて **AWS CLI と Terraform** で行う（講座の進め方）

## 1. 用語

| 用語 | 意味 |
|---|---|
| AWS | Amazon が貸し出す、インターネット上のサーバー・DB・ファイル置き場などの部品の集まり。使った分だけ料金がかかる |
| リージョン | AWS のデータセンターがある地域。このアプリはシドニー（`ap-southeast-2`。理由は 2.3 の 10） |
| SCP（サービスコントロールポリシー） | AWS Organizations（複数のアカウントをまとめる仕組み）で、アカウントごとに「使ってよい範囲」の上限を決める決まり。IAM の権限より上の段にあり、SCP が禁止したものは IAM で許しても使えない |
| AWS Projects | このアカウントが入っている、AWS の利用の枠組み（プロジェクト名「Touch Grass Later」）。月の支出の上限を決められる。SCP で使えるリージョンが決まっている |
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
| 1 | VPC・パブリックサブネット・セキュリティグループ・EC2（Java 25・Nginx 入り） | SSH でログインでき、Java 25 が入っている。ブラウザで Nginx の初期のページが出る | 完了（#65。2026-11-04） |
| 2 | RDS（PostgreSQL）。EC2 からだけつながる | EC2 から `psql` でつながる。パソコンからはつながらない | 完了（#67。2026-11-04） |
| 3 | アプリを EC2 に載せる（画面のファイルは Nginx、API は Spring Boot。Nginx が `/api` を転送する） | `http://（EC2 の IP アドレス）` でログイン画面が出て、操作できる | 完了（#69。2026-11-04） |
| 4 | CloudFront を EC2 の前に置き、HTTPS にする（us-east-1 が SCP で拒否されているので、CloudFront を作れるかを最初に確かめる。作れなければ別の方法を相談する） | `https://xxxx.cloudfront.net` で使える（受け入れ基準 AC-44〜AC-54） | 未着手 |

### 2.2 段階1の構成

```
自分のパソコン（IP アドレスを1つだけ許す）
   │  SSH（22）・HTTP（80）
   ▼
AWS シドニーリージョン（ap-southeast-2）
└─ VPC（10.0.0.0/16）
    └─ パブリックサブネット（10.0.1.0/24・ap-southeast-2a）
        └─ EC2（t4g.micro・Amazon Linux 2023・Java 25・Nginx）
```

段階3でアプリを載せたあとの、EC2 の中の形：

```
ブラウザ ─80─▶ Nginx ─┬ /       → 画面のファイル（パソコンで npm run build したもの）
                      └ /api/…  → Spring Boot（8080。外には開けない）
```

開発中に Vite（5173）が `/api` を Spring Boot へ転送しているのと同じ形。

### 2.3 決めたこと

| # | 項目 | 決定 | 理由 |
|---|---|---|---|
| 1 | 作り方 | AWS CLI と Terraform。画面での手作業はしない | 同じものを作り直せる。Git に履歴が残る。AI がファイルを書いて進められる（講座の進め方） |
| 2 | AWS CLI の認証 | IAM ユーザーのアクセスキー（`aws configure`） | 講座と同じ形で、手順が少ない。鍵の守り方は 8章 |
| 3 | 進め方 | 段階に分ける（2.1） | 問題が起きたとき、どこが原因かがわかりやすい（講座の進め方） |
| 4 | 画面の配り方 | 同じ EC2 の Nginx（80）が画面のファイルを配り、`/api` を Spring Boot（8080）へ転送する。S3 は使わない。EC2 に Node.js は入れない（画面はパソコンでビルドして送る） | 講座と同じく画面と API を同じサーバーに置く。画面と API が同じ住所になり、CORS の設定がいらない（詳細設計書の方針どおり）。開発中の Vite と同じ形で説明しやすく、サーバーのコードを変えずに済む。Spring Boot を外に開けなくてよい |
| 5 | HTTPS | 段階4で CloudFront の無料の住所（`*.cloudfront.net`）を使う。ドメインは買わない | ログインがあるので HTTPS は必須（NS-02）。ドメインの購入はクレジットで払えないことがある |
| 6 | EC2 へのログイン | SSH（鍵ファイル）。ポート 22 は自分の IP アドレスだけに許す | 講座と同じ方法。ファイルの送り込み（`scp`）も簡単 |
| 7 | 段階1〜3の HTTP（80） | 自分の IP アドレスだけに許す。段階4で CloudFront からだけに変える | HTTPS になる前のログイン画面を、他人に使わせない |
| 8 | EC2 の大きさ | t4g.micro（ARM・メモリ 1GB） | 月 20 USD の支出の上限に収まる。Java は ARM でもそのまま動く |
| 9 | state の置き場所 | パソコンの `terraform/terraform.tfstate`（Git には入れない） | 作業するのが1人なので、共有の置き場所（S3 など）はまだいらない |
| 10 | リージョン | シドニー（`ap-southeast-2`）。講座の教材は東京 | このアカウントは AWS Projects の SCP で、シドニー以外のリージョン（東京・大阪・ソウル・シンガポール・us-east-1 など）を使えない（2026-11-04 に確認）。日本からは画面の反応が東京より 0.1 秒ほど遅くなるが、ポートフォリオとしては問題ない |
| 11 | DB | 講座と同じく RDS（6章）。デプロイを終えて画面を確かめたら、すぐ `terraform destroy` で消す | RDS を足すと月 約 34 USD になり、月 20 USD の上限を超える。ずっと公開しておくのではなく、確かめたら消す前提なので、上限を超える前に消せる（2026-11-04 にユーザーが決めた） |
| 12 | DB のパスワードの渡し方 | パラメータストア（暗号化）に入れ、EC2 の IAM ロール（そのパラメータを読むだけ）で起動の前に読む。パスワードは Terraform が作り、state に残さない（7.2） | 要件 NS-12「本番では AWS のパラメータ管理機能に入れる」。パラメータストアの標準の機能は無料。だれもパスワードを見ずに済む（2026-11-04 にユーザーが選んだ） |
| 13 | EC2 への送り込み | ssh-agent に預けた鍵で、スクリプト（`scripts/`）を AI が実行する | パスフレーズを AI に渡さずに、AI が送り込みと確認をできる（2026-11-04 にユーザーが選んだ） |

## 3. 料金とクレジット

### 3.1 プランとクレジット・支出の上限

このアカウント（2026-11-03 に無料プランで作成）は、2026-11-04 に AWS Projects の中で**有料プラン**に切り替えた（ユーザーがコンソールで行った）。

| 項目 | 内容 |
|---|---|
| プラン | 有料プラン（PAID） |
| クレジット | **120 USD**（無料プランのときにもらった分を、切り替えたあとも持っている）。使った料金はクレジットから引かれる |
| 月の支出の上限 | **20 USD**（AWS Projects の設定） |
| 「高度な機能」 | **有効にしない**。有効にすると、月 20 USD の上限が外れる |

- 月 20 USD の上限に収まる構成だけを作る（RDS・NAT ゲートウェイ・ロードバランサーは、上限を超えるか近づくので使わない）
- 日本の住所のアカウントでは、AWS の料金に消費税（10%）がかかる。クレジットで税金まで払えるか、上限が税込みか税抜きかは確かめていない（2026-11-04 時点）。請求の画面で、毎月の料金を確かめる

残りのクレジットは、次のコマンドで見られる。

```bash
aws freetier get-account-plan-state
```

### 3.2 料金の目安（シドニー・2026-11-04 時点の公開価格）

AWS の価格の API（`aws pricing get-products`）で調べた値。

| もの | 料金 | ひと月（730時間） |
|---|---|---|
| EC2 t4g.micro | 0.0106 USD/時 | 約 7.7 USD |
| EC2 t4g.small | 0.0212 USD/時 | 約 15.5 USD |
| 外から見える IP アドレス（IPv4） | 0.005 USD/時 | 約 3.7 USD |
| EC2 のディスク（gp3） | 0.096 USD/GB・月 | 20GB で 約 1.9 USD |
| RDS db.t4g.micro（PostgreSQL・1か所） | 0.025 USD/時 | 約 18.3 USD（＋ディスク） |
| CloudFront | 月 1TB の転送まで無料 | 0 USD |

段階1の構成（t4g.micro ＋ IPv4 ＋ ディスク 20GB）は**ひと月 約 13.3 USD（1日 約 0.44 USD）**。段階2の RDS（db.t4g.micro ＋ gp3 20GB。0.138 USD/GB・月）を足すと**1日 約 1.1 USD（ひと月 約 34 USD）**で、月 20 USD の上限を超えるので、ずっと動かしておかない。使わない時期は `terraform destroy` で消せば料金はかからない（5.6）。RDS のパスワードの預け先（Secrets Manager）は 1つ月 0.40 USD（日割り）。

### 3.3 予算のアラート

AWS Budgets に「ゼロ支出予算」（料金が 0.01 USD を超えたらメール）を作ってある（要件 NO-01・受け入れ基準 AC-56）。段階1から料金がかかり始めたので、メールが届くようになる。月の予算額（例：15 USD）で知らせる形に変えるかを検討する。

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
4. **AWS Projects で有料プランにし、月の支出の上限を 20 USD にする**（2026-11-04 にユーザーが行った。「高度な機能」は有効にしない。3.1）

### 4.3 アクセスキーを作り、AWS CLI に設定する

1. IAM →「ユーザー」→ 作ったユーザー →「セキュリティ認証情報」→「アクセスキーを作成」→ ユースケースは「コマンドラインインターフェイス (CLI)」
2. 表示されたシークレットアクセスキーは、この画面でしか見られない。CSV をダウンロードする場合は、**プロジェクトのフォルダの外**に置く
3. PowerShell で設定する（チャットや Git に鍵を書かない）

```powershell
aws configure
# AWS Access Key ID     : （アクセスキー）
# AWS Secret Access Key : （シークレットアクセスキー）
# Default region name   : ap-southeast-2
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
| `security_groups.tf` | EC2 のセキュリティグループ（22 と 80 を自分の IP アドレスだけに許す） |
| `ec2.tf` | SSH の公開鍵の登録と EC2。起動時に Java 25 と Nginx を入れる |
| `outputs.tf` | 作ったあとに表示する値（IP アドレス・SSH のコマンドなど） |
| `.terraform.lock.hcl` | プロバイダーの版を固定する記録。Git に入れる |
| `.gitignore` | state・`terraform.tfvars`・鍵などを Git に入れないための設定 |

### 5.2 SSH の鍵を作る

パソコンで鍵の組（秘密鍵と公開鍵）を作る。AWS に渡すのは公開鍵（`.pub`）だけ。

```bash
ssh-keygen -t ed25519 -f ~/.ssh/taskboard_ec2 -C "taskboard-ec2"
```

PowerShell では `~` がホームのフォルダにならないので、`$HOME` を使う。

```powershell
ssh-keygen -t ed25519 -f "$HOME\.ssh\taskboard_ec2" -C "taskboard-ec2"
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

PowerShell では：

```powershell
ssh -i "$HOME\.ssh\taskboard_ec2" ec2-user@（terraform output で出た IP アドレス）
```

鍵にパスフレーズを付けた場合は、ここで聞かれる（AI の側からは入力できないので、SSH での確認はユーザーが行う）。

EC2 の中で：

```bash
java -version             # 25 が出る（起動から数分かかることがある。出なければ少し待つ）
systemctl status nginx    # active (running) と出る（q で戻る）
exit
```

ブラウザで `terraform output -raw app_url` の URL（`http://（IP アドレス）`）を開くと、Nginx の初期のページが出る。

セキュリティグループが自分の IP アドレスだけを許しているか：

```bash
aws ec2 describe-security-group-rules \
  --filters Name=group-id,Values=$(aws ec2 describe-security-groups --filters Name=group-name,Values=taskboard-ec2-sg --query "SecurityGroups[0].GroupId" --output text) \
  --query "SecurityGroupRules[?!IsEgress].[FromPort,CidrIpv4]" --output text
# 22 と 80 の行に、自分の IP アドレス/32 だけが出る（8080 は出ない）
```

### 5.6 削除する

```bash
cd terraform
terraform plan -destroy   # 何が消えるかを確かめる
terraform destroy         # すべて消す（料金がかからなくなる）
```

消したあとも、`terraform apply` でいつでも同じものを作り直せる（IP アドレスは変わる）。

## 6. 段階2：RDS（PostgreSQL）

### 6.1 構成

```
AWS シドニーリージョン（ap-southeast-2）
└─ VPC（10.0.0.0/16）
    ├─ パブリックサブネット（10.0.1.0/24・2a）
    │   └─ EC2 ──5432──┐
    ├─ プライベートサブネット（10.0.11.0/24・2a）◀─┘
    │   └─ RDS（PostgreSQL 18.6・db.t4g.micro）
    └─ プライベートサブネット（10.0.12.0/24・2b）  ※ RDS の決まりで2か所必要。DB は 2a だけで動く
```

- プライベートサブネットには、インターネットへの道がない（NAT ゲートウェイは使わない）
- RDS のセキュリティグループは、5432 を **EC2 のセキュリティグループからだけ**許す（IP アドレスではなく「グループ」を相手にするので、EC2 の IP アドレスが変わっても直さなくてよい）。自分のパソコンからもつながらない（要件 NS-14）

### 6.2 ファイル

| ファイル | 中身 |
|---|---|
| `network.tf` | プライベートサブネット2つと、その道案内の表を足した |
| `security_groups.tf` | RDS のセキュリティグループを足した |
| `rds.tf` | RDS の区画の組（サブネットグループ）と RDS |
| `variables.tf` | RDS の版（`db_engine_version`）と大きさ（`db_instance_class`）を足した |
| `outputs.tf` | RDS の住所、パスワードの預け先（Secrets Manager）の場所、`psql` のコマンドを足した |
| `.gitattributes` | Terraform のファイルを、Git がいつも LF（Linux の改行）で取り出すようにする（9章） |

### 6.3 RDS の設定

| 項目 | 設定 | 理由 |
|---|---|---|
| 版・大きさ | PostgreSQL 18.6・db.t4g.micro | 開発用の DB と同じ 18 系。いちばん小さい大きさ |
| ディスク | gp3 20GB（最小）・暗号化 | |
| 置き場所 | 2a の1か所（シングル AZ）・外から見えない（`publicly_accessible = false`） | 予備を置くと料金が倍。EC2 と同じ場所にすると、データセンターをまたぐ通信の料金がかからない |
| DB 名・管理者 | `task_app`・`dbadmin` | アプリ用のユーザー（必要な権限だけ）は段階3で作る |
| 管理者のパスワード | AWS が作り、**Secrets Manager**（秘密の値の預け先）に入れる（`manage_master_user_password`） | Terraform のファイルにも state にもパスワードが残らない（要件 NS-12） |
| バックアップ | 自動バックアップ7日分 | 要件 NO-02。DB の大きさまでの保存は無料 |
| 消すとき | 最後のスナップショット（DB の写し）を取らない | デプロイを確かめたらすぐ消す前提。写しを残すと料金がかかり続ける |

料金は1日 約 0.69 USD（本体 0.6・ディスク 0.09）。段階1と合わせて **1日 約 1.1 USD**（3.2）。

### 6.4 作る

5.4 と同じ（`plan` を見てから `apply`）。段階2の `plan` は「9 to add, 0 to change」（プライベートサブネット2つ・道案内の表とその結びつけ2つ・RDS のセキュリティグループとルール・サブネットグループ・RDS）。RDS は作るのに 8分ほどかかる。

**EC2 に「change」が出たら止まって確かめる**。2026-11-04 には、`ec2.tf` の改行が CRLF に変わったせいで、EC2 の起動時の命令（`user_data`）が変わったと判定された（そのまま `apply` すると EC2 が起動し直して IP アドレスが変わる）。9章を参照。

### 6.5 確かめる（パスワードを扱うので、ユーザーが行う）

1. パソコンの PowerShell で、パスワードを取り出す（画面に出たものをコピーする。チャットや Git に貼らない）

```powershell
cd C:\dev\Raise_Tech\beginner_version\terraform
(aws secretsmanager get-secret-value --secret-id (terraform output -raw db_master_secret_arn) --query SecretString --output text | ConvertFrom-Json).password
```

2. EC2 に SSH で入り、PostgreSQL のクライアント（`psql`）を入れて、RDS につなぐ。`psql` のコマンドは `terraform output -raw psql_command` でも出る

```bash
sudo dnf install -y postgresql17     # 1回だけ。ない場合は dnf list 'postgresql1*' で入れられる版を見る
psql "host=（RDS の住所） port=5432 dbname=task_app user=dbadmin sslmode=require"
```

3. パスワードを聞かれたら貼り付ける（画面には出ない）。つながったら：

```sql
select version();   -- PostgreSQL 18.6 … と出る
\q
```

- `sslmode=require` は、通信を暗号化してつなぐ指定（RDS の PostgreSQL は、暗号化しない接続を断る）
- `psql` は Terraform の起動時の命令（`user_data`）には入れていない（入れると EC2 が起動し直して IP アドレスが変わるため）。EC2 を作り直したときは、2 の `dnf install` をもう一度行う

外から見えないことの確かめ方（パソコンで）：

```bash
aws rds describe-db-instances --db-instance-identifier taskboard-db --query "DBInstances[0].PubliclyAccessible"   # false
```

RDS の住所をパソコンで引くと、VPC の中の住所（`10.0.11.x`）になり、5432 にはつながらない。

### 6.6 削除する

5.6 と同じ `terraform destroy` で、EC2 と一緒に消える（最後のスナップショットは取らない。自動バックアップも消える）。RDS は消すのにも 5〜10分かかる。

## 7. 段階3：アプリを載せる

### 7.1 EC2 の中の形

```
ブラウザ ─80─▶ Nginx ─┬ /       → /opt/taskboard/web（画面のファイル）
                      └ /api/…  → Spring Boot（127.0.0.1:8080 だけで待ち受け）─5432─▶ RDS
```

| もの | 置き場所 | 中身 |
|---|---|---|
| 画面のファイル | `/opt/taskboard/web/` | パソコンで `npm run build` した `frontend/dist/` |
| Spring Boot | `/opt/taskboard/app/taskboard.jar` | パソコンで `./gradlew bootJar` した jar |
| 起動の仕組み | `/etc/systemd/system/taskboard.service` | EC2 の起動時と、落ちたときに Spring Boot を起動する。アプリ専用のユーザー `taskboard`（root ではない。ログインできない）で動かす |
| 起動の前の処理 | `/opt/taskboard/bin/start.sh` | パラメータストアから DB のパスワードを読み、環境変数で Spring Boot に渡す |
| Spring Boot の設定 | `/etc/taskboard/taskboard.env` | 本番の設定（プロファイル `prod`）・RDS の住所・ユーザー名・パラメータの名前・Java のメモリ。**パスワードは入っていない** |
| Nginx の設定 | `/etc/nginx/nginx.conf`・`taskboard-headers.conf` | 上の図の転送と、画面のファイルに付けるセキュリティのヘッダー |
| スワップ | `/swapfile`（1GB） | メモリ（1GB）が足りなくなったときに、ディスクで補う |

これらの元のファイルは、リポジトリの `deploy/ec2/` にある。

### 7.2 DB のパスワードの渡し方（要件 NS-12）

```
Terraform ──（その場限りの値を作り、書き込むだけ）──▶ パラメータストア /taskboard/db/app-password（暗号化）
                                                          │ 読む（EC2 の IAM ロール。このパラメータを読む権限だけ）
EC2 の start.sh ──（環境変数 DB_PASSWORD）──▶ Spring Boot
```

- パスワード（英数字32文字）は Terraform が作るが、**state にもファイルにも残らない**（`ssm.tf`。`ephemeral` と `value_wo`）。だれの画面にも出ない
- EC2 に付ける IAM ロール（`iam.tf`）でできるのは、このパラメータを読むことだけ。ほかのパラメータは読めない
- アプリ用の DB ユーザー `task_app_user` は、ユーザーが EC2 で `create-app-db-user.sh` を1回実行して作る（7.4）。権限は `task_app` の DB につなぐことと、`public` スキーマにテーブルを作って使うことだけで、管理者の権限はない（要件 NS-14）。テーブルは、アプリの起動時に Flyway が `task_app_user` として作る
- パスワードを作り直すとき：`ssm.tf` の `value_wo_version` を 1 つ増やして `apply` → `create-app-db-user.sh` をもう一度実行（パスワードを合わせ直す）→ `sudo systemctl restart taskboard`

### 7.3 ファイル

| ファイル | 中身 |
|---|---|
| `terraform/ssm.tf` | パラメータストアの DB パスワード |
| `terraform/iam.tf` | EC2 の IAM ロール（パラメータを読むだけ） |
| `terraform/ec2.tf` | EC2 に IAM ロールを付けた（EC2 は起動し直さない） |
| `backend/src/main/resources/application-prod.yml` | 本番だけの設定：`server.address: 127.0.0.1`、`server.forward-headers-strategy: native`（詳細設計書 3.7・3.8.8） |
| `deploy/ec2/` | EC2 に置くファイル（7.1 の表）と、DB ユーザーを作るスクリプト |
| `scripts/common.sh` | 下の3つが共通で使う設定（EC2 の IP アドレスは Terraform の出力から読む） |
| `scripts/setup-server.sh` | EC2 の準備（最初に1回。`deploy/ec2/` を変えたときも。何度実行してもよい） |
| `scripts/deploy-backend.sh` | jar を作って送り、Spring Boot を起動し直す。動き出すまで待つ |
| `scripts/deploy-frontend.sh` | 画面のファイルを作って送り、入れ替える |
| `.gitattributes` | `.sh` と `deploy/` を LF で取り出す（EC2 の Linux で正しく動かすため） |

### 7.4 載せる

SSH の鍵を ssh-agent（鍵を預かって SSH に渡す仕組み）に預けておくと、スクリプトがパスフレーズを聞かずに動く（AI も SSH を使える）。

```powershell
# 最初に1回だけ（PowerShell を「管理者として実行」で開いて）
Set-Service -Name ssh-agent -StartupType Manual
Start-Service ssh-agent
# パソコンを起動し直したら、そのたびに（ふつうの PowerShell で。パスフレーズを聞かれる）
ssh-add "$HOME\.ssh\taskboard_ec2"
```

スクリプトは Git Bash で、`beginner_version` で実行する。SSH は Windows の OpenSSH（ssh-agent を使える）を使う。

```bash
bash scripts/setup-server.sh      # 1. EC2 の準備
```

```powershell
# 2. アプリ用の DB ユーザーを作る（ユーザーが PowerShell で。dbadmin のパスワードを聞かれるので、6.5 の方法で取り出して貼る）
ssh -t ec2-user@（EC2 の IP アドレス） sudo /opt/taskboard/bin/create-app-db-user.sh
# 最後に task_app_user の rolsuper・rolcreatedb・rolcreaterole が f（なし）と出る
```

```bash
bash scripts/deploy-backend.sh    # 3. サーバー（先に ./gradlew check を通す）
bash scripts/deploy-frontend.sh   # 4. 画面（先に npm run lint・npm test を通す）
```

2回目からは、変えたほうの 3 か 4 だけを実行すればよい。

### 7.5 確かめる

| 確かめること | 方法 | 2026-11-04 の結果 |
|---|---|---|
| 画面と操作 | ブラウザで `http://（EC2 の IP アドレス）`。登録・ログイン・ボード・リスト・カード（追加・変更・ドラッグ＆ドロップ・削除）・ゲスト | ユーザーが確認。問題なし |
| URL を直接開く | `/boards/1` を開く・再読み込み | 画面が出る（200） |
| API の転送 | `curl http://（IP）/api/auth/me` | 401（ログインしていないので正しい） |
| 本当の接続元 | ログインを1回わざと失敗させ、`sudo journalctl -u taskboard \| grep ログインに失敗` | 自分の IP アドレスが出る（`127.0.0.1` ではない） |
| 自動で動き出すか | `sudo systemctl reboot` のあと、ブラウザで開く | Spring Boot・Nginx ともに動き出した |
| Spring Boot が外から届かないか | `sudo ss -ltnp \| grep 8080` | `127.0.0.1:8080` だけ |
| 画面のヘッダー | `curl -I http://（IP）/` | `Content-Security-Policy: frame-ancestors 'none'`・`X-Frame-Options: DENY`・`X-Content-Type-Options: nosniff`。Nginx の版は出ない |

よく使うコマンド（EC2 の中で）：

```bash
sudo systemctl status taskboard          # Spring Boot の状態
sudo journalctl -u taskboard -n 100      # Spring Boot のログ（最後の100行）
sudo journalctl -u taskboard -f          # ログを流し続ける（Ctrl + C で止める）
sudo systemctl restart taskboard         # 起動し直す
free -m                                  # メモリ
```

## 8. 安全のための決まり

- **秘密の値（アクセスキー・SSH の秘密鍵・DB のパスワード）は、チャット・Git・プロジェクトのフォルダに出さない**（要件 NS-12）。DB の管理者のパスワードは Secrets Manager、アプリ用のパスワードはパラメータストアに置く。管理者のパスワードは使うときにユーザーが取り出す。AI はどちらのパスワードも取り出さない
- アクセスキーは期限がないので、長く使わない時期は IAM の画面で「無効」にする。漏れたかもしれないときは、すぐ削除して作り直す
- ルートユーザーのアクセスキーは作らない。ルートユーザーはコンソールで、MFA を付けて使う
- 外からの入口は、必要なポートを、必要な相手にだけ開ける（`0.0.0.0/0` で開けない。`variables.tf` の入力チェックで `/32` 以外を断る）。Spring Boot の 8080 は外に開けない
- Terraform で作ったものを、コンソールで手で直さない（ファイルと実物がずれる）
- `terraform apply`・`destroy` は、`plan` の結果を確かめてから

## 9. 困ったとき

| 症状 | 原因と対処 |
|---|---|
| `aws` や `terraform` が見つからない | 入れたあとで VS Code（Cursor）を開き直していない |
| `explicit deny in a service control policy` で断られる | AWS Projects の SCP で、シドニー（`ap-southeast-2`）以外のリージョンが禁止されている。`--region` や `aws configure` のリージョンがシドニーか確かめる。IAM の権限を足しても解決しない（SCP は IAM より上の段の決まり）。2026-11-03 には、リージョンが東京だったために EC2・S3 などがすべて断られた |
| SSH がつながらない（`Connection timed out`） | 家の IP アドレスが変わった。5.3 で `my_ip_cidr` を直し、`plan`・`apply` する |
| `java -version` が見つからない・ブラウザで開けない | 起動直後で、Java と Nginx をまだ入れている途中。数分待つ。`sudo cat /var/log/cloud-init-output.log` で進み具合を見られる |
| Git Bash で `aws ssm …` に渡したパスが `C:/Program Files/Git/…` に化ける | Git Bash がパスを Windows の形に直している。前に `MSYS_NO_PATHCONV=1` を付ける |
| `plan` で、変えていない EC2 の `user_data` が変わったと出る | `.tf` の改行が CRLF（Windows の形）になっている。このパソコンの Git は、取り出すときに CRLF に変える設定（`core.autocrlf=true`）のため。`terraform/.gitattributes` で LF に固定した（2026-11-04）。それでも出たら、改行を LF に直してから `plan` し直す。そのまま `apply` しない |
| PowerShell で `jq` が見つからない | jq を入れる前から開いていた窓。開き直すか、`ConvertFrom-Json` を使う（6.5） |
| `psql` で `no pg_hba.conf entry … no encryption` と断られる | 暗号化していない接続。`sslmode=require` を付ける |
| `create-app-db-user.sh` で `taskboard.env: line 6: -Xmx384m: command not found` | `taskboard.env` の `JAVA_OPTS` を `"` で囲んでいない（スペースを含む値はシェルで読めない）。`setup-server.sh` を最新にして実行し直す（2026-11-04 に直した） |
| `deploy-backend.sh` が「3分たっても動き出しません」 | EC2 で `sudo journalctl -u taskboard -n 100` を見る。DB につながらない（`task_app_user` を作っていない・パスワードが合っていない）ことが多い。7.2 の作り直しの手順でパスワードを合わせる |
| スクリプトで `Permission denied (publickey)` やパスフレーズを聞かれる | ssh-agent に鍵が入っていない（パソコンを起動し直すと消える）。`ssh-add "$HOME\.ssh\taskboard_ec2"` |
| パソコンでサーバーのテスト（Testcontainers）が `Connection reset` で失敗する | Docker Desktop の、パソコンとコンテナの間の通信の不調。2026-11-04 に WSL が更新されたあとに起きた。Docker Desktop を起動し直しても直らないときは、パソコンを再起動する。GitHub Actions のテストでも確かめられる |

## 10. 改訂履歴

| 版 | 日付 | 内容 |
|---|---|---|
| 1.0 | 2026-11-03 | 新規作成。用語、構成と段階、料金とクレジット、準備、段階1（ネットワークと EC2）（#65） |
| 1.1 | 2026-11-04 | リージョンを東京からシドニーに変更（SCP の制限）。有料プラン・クレジット・月 20 USD の上限に合わせて料金の章を書き直した。段階1を作成して確かめた（#65） |
| 1.2 | 2026-11-04 | 段階2（RDS）を追加（6章）。安全のための決まり・困ったとき・改訂履歴の章番号を1つずらした（#67） |
| 1.3 | 2026-11-04 | 段階3（アプリを載せる）を追加（7章）。決めたことに 12・13 を足した。安全のための決まり・困ったとき・改訂履歴の章番号を1つずらした（#69） |
