# 引き継ぎ書（2026-11-04 更新）

前のチャットの続きです。以下を読んでから作業を再開してください。

**最初にすること**
1. PR（#75：AWS を消したことを資料に書く）がマージ済みかを `gh pr list --state all --limit 3` で確かめる。マージ済みなら `main` に移って `git pull`。まだなら、私にマージを頼む
2. 下の「次にやること」から、次に作るものを私と決める
3. AWS を作り直したときは、Claude が EC2 に SSH するには、私が ssh-agent に鍵を入れておく必要がある（パソコンを起動し直すと消える。`ssh-add "$HOME\.ssh\taskboard_ec2"`）

**サーバーの状態**：前のチャットの終わり（2026-11-01）に、DB・Spring Boot・Vite をすべて止めた（ほかのチャットで起動したときにポートがぶつかってエラーになるため）。ポート 5432・8080・5173 は空いている。DB はコンテナを止めただけなので、データ（`demo@example.com` など）は残っている。動作確認のときは、スキル `dev-servers` の手順で DB → Spring Boot → Vite の順に起動する。チャットを終えるときも、起動したサーバーは止めておく

**AWS の状態**：**何もない**（2026-11-04 に、4つの段階を確かめたあと `terraform destroy` で 26個すべて消した。料金はかかっていない。AWS CLI で残りが0件であることも確かめた）。作り直すときは `deployment.md` 9.3（30分ほど。住所が変わり、DB は空から始まる。RDS があると1日 約 1.1 USD かかり、月 20 USD の上限を超えるので、確かめたらまた消す）。`apply`・`destroy` は私の OK をもらってから

**最初に読むファイル**（すべて `beginner_version/` の中）
1. `docs/handoff.md`（このファイル）
2. `CLAUDE.md`：AI が守るルール（GitHub の運用・サーバーのポート・コードの書き方・コマンド）
3. `docs/detailed-design.md`：詳細設計書（実装の中心。使っているバージョンは 5.1）
4. 必要に応じて：`docs/requirements.md`（要件）、`docs/database-design.md`（テーブル）、`docs/api-design.md`（API）、`docs/screen-design.md`（画面）、`mockup/`（動くモックアップ。見た目と動きの手本）

## プロジェクト
- 講座の課題で、トレロ風のタスク管理アプリ「TaskBoard」（仮）を作っている。作業フォルダは `C:\dev\Raise_Tech\beginner_version`（Git リポジトリ）。完成したらポートフォリオとして公開する
- 講座の目的は、AI と相談しながら「要件定義 → 設計 → 環境構築 → 実装 → テスト → デプロイ」を一通り経験すること。締め切りはない
- 技術：Java 25 / Spring Boot 4.1、React 19（Vite・TypeScript）、PostgreSQL 18、AWS（講座の指定）。細かいバージョンは `detailed-design.md` 5.1
- 私（ユーザー）は Web 制作の経験はあるが、プログラミングは未経験。コードは基本的に AI に書いてもらう。ただし自分で説明できるように、何をしているかを解説してほしい

## 進め方の希望
- 講義動画を見ながら進めている。私が講義の画面（Claude Code への指示）の画像を貼るので、このプロジェクトに合わせた進め方を提案してほしい（講義のアプリはログインなしの単純なもので、このアプリとは違う）
- 決めごとは **質問を1つずつ出し、おすすめと選択肢を示す形式**。決まったら「ここまでの決定」としてまとめる
- 勉強のため、あえて難しいほうを選ぶことがある
- 作業ごとに **イシュー → ブランチ → PR → 私が GitHub の画面で Squash and merge**（`CLAUDE.md` の「GitHub の運用ルール」）。イシューは作る前に中身を見せる
- 画面ができたら、私がブラウザで確かめてから PR を作る。確かめる手順（何を押すと何が起きるか）を示してほしい
- 機能を作ったら、README の「進み具合」と、この引き継ぎ書も直す
- 応答はすべて日本語（`C:\dev\Raise_Tech\.claude\CLAUDE.md`）

## 今の状態（2026-11-04）

**できていること**（講義の CRUD はカード・リスト・ラベルについてひととおり完了。ドラッグ＆ドロップも完了）

| 機能 | API | 画面 |
|---|---|---|
| 登録・ログイン・ログアウト・自分 | A-01・A-02・A-04・A-05 | W-01 ログイン、W-02 登録、ヘッダーのメールアドレスとログアウト |
| ボードの取得・ボード名の変更 | A-06・A-07 | W-03 ボード（`/boards/{ID}`。ボード名はクリックで変更） |
| 複数ボード（一覧・追加・削除） | A-22・A-23・A-24（要件 B-02〜B-04） | W-11 ボード一覧（`/`）、W-03 の「← ボード一覧」と、ボード名の横の「…」（☰）の「ボードを削除」 |
| リストの追加・名前の変更・削除 | A-08・A-09・A-10 | 「＋ リストを追加」、名前をクリックで変更、「…」メニューから削除 |
| カードの追加・保存・削除 | A-12・A-13・A-14 | 「＋ カードを追加」、W-04 カード詳細（全項目の保存・削除） |
| ラベルの付け外し | A-13 の `labelIds` | カード詳細の「＋」 |
| ラベル管理（追加・変更・削除） | A-16・A-17・A-18 | W-05。ヘッダーの「ラベル管理」と、カード詳細のラベル一覧の下の「ラベルを管理」（カード詳細の上に重ねて開く） |
| カードのゴミ箱 | C-03、A-14 | W-03。カードにマウスを乗せる（Tab で選ぶ）と右上に出る。確認してから削除（`CardDeleteButton`）。スマートフォンでは出さない |
| ドラッグ＆ドロップ（リストの並び替え、カードの並び替え・移動） | A-11・A-15 | W-03。リストは名前を、カードはカード全体をつかむ。キーボードでも動かせる |
| ゲストログイン・ログアウトでの削除・24時間後の削除 | A-03・A-04・A-05 | W-01 の「ゲストとして試す」、W-03 の「ゲスト」の札と注意書きの帯 |
| GitHub Actions（自動テスト） | 要件 5章 | `.github/workflows/ci.yml`。PR と main への反映で `backend`・`frontend` が走り、成功しないとマージできない（`detailed-design.md` 5.3） |
| ログイン失敗・ゲスト作成・ユーザー登録の回数制限 | U-10、A-01・A-02・A-03 の 429 | W-01・W-02 に「しばらくしてから再度お試しください」（画面のコードは変えていない。API の `detail` をそのまま出す） |
| スマートフォン幅のハンバーガーメニュー | 画面設計書 W-03 | 幅 640px 以下でヘッダーを2段にし、ラベル管理とアカウントの操作を ☰（`MobileMenu`）にまとめる |
| カードの検索・絞り込み | S-01〜S-04（要件 3.8） | W-03 のヘッダーの検索欄と「絞り込み」（`CardSearch`）、絞り込み中の帯（`FilterBar`）。サーバーは変えていない |
| 退会 | U-11、A-19 | ヘッダーのメールアドレス（▾）のメニュー（`UserMenu`）と W-08（`WithdrawModal`）。ゲストにはメニューを出さない |
| メールアドレス・パスワードの変更 | U-12・U-13、A-20・A-21 | ▾ と ☰ のメニューから W-09（`EmailChangeModal`）・W-10（`PasswordChangeModal`）。成功は緑の通知。ゲストには出さない |
| Dependabot・アクセスのログ・CSP | NS-10・NS-13・NO-03 | 画面は変わらない。本番用の HTML に CSP の `<meta>`、サーバーのログにアクセスの1行、GitHub の設定で Dependabot |

- テスト：サーバー 176件（`./gradlew test`）、画面 67件（`npm test`）。すべて成功
- コードのチェック（#27）：サーバーは Checkstyle・SpotBugs（`./gradlew check`）、画面は oxlint の規則を足し、Prettier を入れた（`npm run lint`・`npm run format:check`）。道具と規則は `detailed-design.md` 5.2
- 開発用の DB は Flyway V5（登録の回数制限）まで当たっている（2026-11-01）。確認用のユーザー **`demo@example.com` ／ `password123`** がいる。ラベル（仕事・買い物・あとで）は SQL で入れたもの。2026-11-01 に退会の確認で消えたので、登録の API で作り直した（カードは空）。**退会を試すときは、新しく登録したユーザーで**

**まだないもの**

| 機能 | 要件・設計 | メモ |
|---|---|---|
| AWS へのデプロイ（常に公開しておくこと） | G-04 | 4つの段階（#65・#67・#69・#73）をすべて作り、受け入れ基準 AC-44〜AC-54 を確かめた。料金のため消してある（#75）。作り直す手順は `deployment.md` 9.3 |

## 次にやること
**AWS へのデプロイ（G-04）を、4つの段階に分けて進めている**（`docs/deployment.md` 2.1。講義 初級編19「Terraform で NW・EC2 構築」に合わせた）。**段階1〜4（#65・#67・#69・#73）はすべて完了**。受け入れ基準 AC-44〜AC-54 も確かめた（`deployment.md` 8.5）。決めていたとおり、確かめたあと `terraform destroy` で消した（2026-11-04。#75。`deployment.md` 9章）。以下は各段階のメモ：
1. ~~段階2：DB~~ → RDS で作った（#67）
2. ~~段階3：アプリを載せる~~ → 作った（#69）。以下はそのときのメモ：アプリ用の DB ユーザー（`task_app_user`。必要な権限だけ。NS-14）を作る。Nginx の転送の設定（`/` は画面のファイル、`/api` は Spring Boot の 8080）、Spring Boot の起動のしかた（systemd など）、秘密の値（DB のパスワード）の渡し方（NS-12。「AWS のパラメータ管理機能」は SSM Parameter Store）
3. ~~段階4：CloudFront で HTTPS~~ → 作った（#73）。以下はそのときのメモ：Nginx の `X-Forwarded-For` は今 `$remote_addr`（CloudFront を置くと CloudFront のアドレスになるので、CloudFront のアドレスからだけ信用する設定に変える。詳細設計書 3.8.8）。Cookie の `Secure` が付くことも確かめる。**us-east-1 が SCP で拒否されているので、CloudFront を作れるかを最初に確かめる**。回数制限のための `server.forward-headers-strategy: native` もこのとき（`detailed-design.md` 3.8.8）。80 番を CloudFront からだけに変える
   - 公開の前に、スキル `quality-check` の「デプロイ前のセキュリティチェック」（コード全体を NS-01〜NS-14 で見直す・Dependabot の通知0件）を行う。公開したあとで、受け入れ基準 AC-47〜AC-54 を確かめる（2026-11-01 に決めた。イシュー #63）
4. パスワードの再設定（メール送信が要る。AWS の SES とあわせて。SES がシドニーで使えるかも確かめる）

ほかの候補（AWS の合間に）：受け入れ基準（要件定義書 9章）をもとに、公開の前の手動のテストの一覧表（NT-03）を作る。要件定義書 7章の「対象外」から、作りたいものを選ぶ

**AWS 段階4（#73）で決めたこと**（詳細は `docs/deployment.md` 8章）
- CloudFront（無料の住所 `*.cloudfront.net`）を EC2 の前に置いた。us-east-1 は SCP で拒否されているが、CloudFront は作れた。`/api/*` はキャッシュせず全部渡す（`Managed-AllViewerAndCloudFrontHeaders-2022-06`）、`/assets/*` だけキャッシュ、HSTS（1年）
- **CloudFront と EC2 の間は HTTP のまま**（私の選択。ドメインを買わないため）。EC2 の 80 番は CloudFront の一覧（`com.amazonaws.global.cloudfront.origin-facing`）からだけ。作るのは2回に分けた（先に CloudFront を足して確かめ、あとで私の IP の口を閉じた）
- Nginx：`real_ip`（CloudFront のアドレスからだけ `X-Forwarded-For` を信用）と、`CloudFront-Forwarded-Proto` から `X-Forwarded-Proto: https`。CloudFront のアドレスの一覧（49件）は `setup-server.sh` が `ip-ranges.json` から作る。Cookie の `Secure` が付くことを確かめた
- **IPv6 は切った**（私の選択。回数制限が効かなくなるため）
- Trivy の新しい2件（WAF なし・アクセスの記録なし）は `.trivyignore` で外した。「ほかの人の CloudFront からも EC2 に届く」は、合言葉のヘッダーで防げるが入れなかった（私の選択。短い期間だけ公開する前提）
- `deploy/ec2/psql-app.sh` を足した：アプリ用のユーザーで本番の DB を開く確認用（`ssh -t ec2-user@IP sudo /opt/taskboard/bin/psql-app.sh -c "…"`）。AC-47 はこれで確かめた
- セキュリティグループの説明文は段階1のまま（変えると作り直しになるため）

**品質チェックにインフラを足す（#71）で決めたこと**（講義 初級編19 の最後に合わせた。詳細は `quality-check` の SKILL.md、`deployment.md` 10.1、`detailed-design.md` 5.2）
- `quality-check` に範囲「インフラ」（`terraform/`・`deploy/`・`scripts/`・`application-prod.yml`）を足した。自動チェックは `terraform fmt -check`・`validate`・`plan -detailed-exitcode`・**Trivy**（私が winget で入れた。0.76.1）。AI の見直しの観点（入口・DB・秘密の値・IAM・料金・`plan` の思わぬ変更・改行・Nginx・スクリプト・systemd）。「デプロイ前のセキュリティチェック」にもインフラの全体の見直しを入れた
- Trivy の6件は、理由をつけて `terraform/.trivyignore` で外した（私が A を選んだ）。外したあとは0件
- 直したもの：Nginx で `X-Forwarded-Port` も上書きする（EC2 に反映済み）。`apply` に使った `*.tfplan` は終わったら消す（Trivy が読んでしまうため）
- README の「構成」に本番（AWS）の図（mermaid）を足した
- Claude が `rm` でファイルを消すのは許可で止められる。消すのは私が行う
- Git Bash で `cd` のたびに `fnm`（Node.js の版を切り替える道具）のエラーが出る（Cursor を開き直したあとから。シェルの設定に `fnm env` がない）。`cd … && 次のコマンド` が止まるので、Claude は `-chdir` などで `cd` を避けている

**AWS 段階3（#69）で決めたこと**（詳細は `docs/deployment.md` 7章）
- 形は `ブラウザ → Nginx（80）→ /api だけ Spring Boot（127.0.0.1:8080）→ RDS`。画面は `/opt/taskboard/web`、jar は `/opt/taskboard/app/taskboard.jar`。Spring Boot は systemd（`taskboard.service`）で、アプリ専用のユーザー `taskboard` で動かす。Java は `-Xmx384m`、スワップ 1GB
- **DB のパスワード**（私の選択）：パラメータストア `/taskboard/db/app-password`（SecureString）。Terraform が作るが state に残らない（`ephemeral` ＋ `value_wo`）。EC2 の IAM ロールはこのパラメータを読むだけ。`start.sh` が起動の前に読んで環境変数で渡す。作り直しは `value_wo_version` を増やして apply → `create-app-db-user.sh` → restart
- `task_app_user` は私が EC2 で `ssh -t ec2-user@IP sudo /opt/taskboard/bin/create-app-db-user.sh` を実行して作った（dbadmin のパスワードを貼る）。権限は CONNECT・TEMPORARY と public スキーマの USAGE・CREATE だけ（`f|f|f` を確認）。テーブルは Flyway が `task_app_user` として作った
- **送り込みは Claude が実行**（私の選択）：ssh-agent に預けた鍵で、Git Bash から `bash scripts/setup-server.sh`（準備）・`deploy-backend.sh`・`deploy-frontend.sh`。SSH は Windows の OpenSSH（Git Bash の ssh は Windows の ssh-agent を使えない）。terraform・scp に渡すパソコンのパスは `cygpath -w` で Windows の形にする
- `application-prod.yml`（新規）：`server.address: 127.0.0.1`、`server.forward-headers-strategy: native`。Nginx は `X-Forwarded-For` を `$remote_addr` で上書き（偽装を防ぐ）。ログインの失敗のログに私の IP アドレスが出ることを確かめた
- Nginx のヘッダー（`deploy/ec2/taskboard-headers.conf`）：`frame-ancestors 'none'`・`X-Frame-Options: DENY`・`nosniff`・`Referrer-Policy`。`server_tokens off`。`/assets/` は1年保存、`index.html` は保存させない、ほかは `try_files $uri /index.html`
- 見つけて直したこと：`taskboard.env` の `JAVA_OPTS` を `"` で囲んでいなかったので、シェルで読めなかった（systemd は読める）。`.sh` と `deploy/` は `.gitattributes` で LF に固定
- **パソコンのサーバーのテストが失敗する**（2026-11-04）：Testcontainers の DB への接続が `Connection reset` で切られる。Docker Desktop の、パソコンとコンテナの間の通信の不調（自作のコンテナでも再現）。WSL が更新されていた（3.0.3）。Docker Desktop を起動し直しても直らなかったので、**パソコンの再起動を私に頼んである**。この PR のテストは GitHub Actions で確かめた

**AWS 段階2（#67）で決めたこと**（詳細は `docs/deployment.md` 6章）
- 講義と同じく **RDS**（PostgreSQL 18.6・db.t4g.micro・gp3 20GB・暗号化・2a の1か所・`publicly_accessible = false`・バックアップ7日分）。全体で月 約 34 USD になり、月 20 USD の上限を超えるが、**デプロイを終えて画面を確かめたらすぐ `terraform destroy` で消す**（私が決めた）
- プライベートサブネット2つ（2a・2b。RDS の決まりで2か所必要）。インターネットへの道はなく、NAT ゲートウェイも使わない。RDS の 5432 は **EC2 のセキュリティグループからだけ**
- 管理者 `dbadmin` のパスワードは AWS が作って **Secrets Manager** へ（`manage_master_user_password`）。state にパスワードは入らない。**Claude はパスワードを取り出さない**。EC2 から `psql` でつなぐ確認は私が行った（パスワードの取り出しは PowerShell の `ConvertFrom-Json`。手順は `deployment.md` 6.5）
- 最後のスナップショットは取らない（`skip_final_snapshot`。すぐ消す前提）
- EC2 の `psql` は、私が SSH で `sudo dnf install -y postgresql17` で入れた（`user_data` には入れていない。入れると EC2 が起動し直して IP アドレスが変わるため）
- 見つけて直したこと：このパソコンの Git（`core.autocrlf=true`）が `.tf` を CRLF で取り出し、`plan` で EC2 の `user_data` が変わったと出た（そのまま `apply` すると EC2 が起動し直す）。`terraform/.gitattributes` で LF に固定した。**`plan` で、変えていないものに change が出たら止まって確かめる**

**AWS 段階1（#65）で決めたこと**（詳細は `docs/deployment.md`）
- 作り方は講義と同じく **AWS CLI と Terraform**。コンソールは確認と初期設定だけ。Terraform のファイルは `terraform/`。`apply`・`destroy` は、Claude が `plan` の結果を説明し、私が OK と言ってから（`plan -out` で保存した予定どおりに `apply` する）
- AWS CLI の認証は IAM ユーザー `KeisukeFujita`（`AdministratorAccess`）の**アクセスキー**（`aws configure`。私の選択）。鍵はチャット・Git に出さない。ルートユーザーは MFA あり、アクセスキーは作らない
- **リージョンはシドニー（`ap-southeast-2`）**。アカウントが AWS Projects（プロジェクト名「Touch Grass Later」）の中にあり、SCP でシドニー以外（東京・大阪・us-east-1 など）が拒否される。最初は東京で作ろうとして、EC2・S3 などがすべて `explicit deny in a service control policy` で断られた（AWS サポートにも問い合わせたが、その前に私が ChatGPT と相談して原因がわかった）
- **有料プランに切り替え済み**（2026-11-04。私がコンソールで行った）。クレジット 120 USD、**月の支出の上限 20 USD**。**「高度な機能」は絶対に有効にしない**（上限が外れる）。要件 NO-01 もこれに合わせて直した（要件定義書 1.11）
- 構成：VPC（10.0.0.0/16）・パブリックサブネット 1つ（`ap-southeast-2a`）・EC2 t4g.micro（Amazon Linux 2023・ARM・ディスク 20GB 暗号化・IMDSv2 必須）。起動時に Java 25（Corretto）と Nginx を入れる。NAT ゲートウェイ・ロードバランサーは使わない
- **画面は EC2 の Nginx（80）が配り、`/api` を Spring Boot（8080）へ転送する**（私の選択。講義と同じく Nginx。Spring Boot は外に開けない）。EC2 に Node.js は入れない（画面はパソコンで `npm run build` して送る）
- セキュリティグループは 22（SSH）と 80 だけ、どちらも私の IP アドレス（`terraform.tfvars` の `my_ip_cidr`。Git に入れない）からだけ。`/32` 以外は入力チェックで断る。家の IP アドレスが変わったら直して `apply`
- SSH は鍵 `C:\Users\imhun\.ssh\taskboard_ec2`（ED25519・**パスフレーズ付き**）。Claude はパスフレーズを入れられないので、**SSH での確認は私が PowerShell で行う**（`ssh -i "$HOME\.ssh\taskboard_ec2" ec2-user@（IP）`）。PowerShell の `ssh-keygen` では `~` が使えない（`$HOME` を使う）
- 料金（シドニー）：段階1は月 約 13.3 USD（t4g.micro 7.7・IPv4 3.7・ディスク 1.9）。消費税がクレジットで払えるか・上限が税込みかは未確認
- state はパソコンの `terraform/terraform.tfstate`（Git に入れない）。**消さないこと**（消すと Terraform が作ったものを管理できなくなる）
- AWS CLI を設定したときの質問「AWS skills と MCP サーバーを設定するか」は `n`。jq も入れた（講義に合わせて）

**カードのゴミ箱（#61）で決めたこと**（ユーザーの提案。詳細は `screen-design.md` W-03、要件 C-03・AC-60）
- 押したら、カード詳細と同じ確認ダイアログを出してから消す（すぐ消す・「元に戻す」は選ばなかった）
- 出すのは、マウスを乗せたときと、キーボードでカードの中を選んだとき（CSS の `:hover`・`:focus-within`）。`visibility` で隠すので、隠れている間は Tab で選ばれない。ホバーできない画面（`@media (hover: none)`）とドラッグ中は出さない
- 置き場所は右上。タイトルのボタンの見えない部分（カード全体）より上に重ねる（`z-index: 2`）ので、押しても詳細は開かず、ドラッグも始まらない。長いタイトルと重なっても読めるように白い丸の背景
- 絞り込み中も消せる（カード詳細から消せるのと同じ）
- 受け入れ基準の番号は足した順の通し番号（今回は AC-60。表の途中に入れても番号は振り直さない）

**テーブル設計書の見直し（#59）で決めたこと**
- 版を付けた（今は 1.8）。改訂履歴は 13章（Flyway の V1〜V5 との対応も書いた）。本文の「いつ・どのイシューで」は履歴へ移した
- 3章の先頭にテーブルの一覧（論理名・物理名・中身・作る SQL）。セッションの2つも一覧には入れた（列の定義は書かない）。列の表は「論理名・物理名・型・NULL・初期値・内容」
- CRUD 表は 12章。縦に API（A-01〜A-24）と毎時の自動の処理、横にテーブル。`ON DELETE CASCADE` で消えるものは「D*」。テーブルや API を変えたら、この表も直す
- 古い記述を直した：ER図の `boards.user_id` の「UK」、3.8 のトリガーの対象

**Dependabot・アクセスのログ・CSP（#57）で決めたこと**（詳細は `detailed-design.md` 3.11・4.9・5.4）
- Dependabot：脆弱性の通知（alerts）と、直した版へ上げる PR（security updates）を、リポジトリの設定で有効にした。毎週の版上げ（`dependabot.yml`）は使わない。Dependabot の PR が来たら、GitHub Actions の結果を見て私がマージを決める
- アクセスのログ：`AccessLogFilter` が、1つのリクエストごとに `GET /api/boards/3 200 12ms（ユーザーID 5）` を出す。クエリ文字列は出さない。Spring Security の部品の中（`SecurityContextHolderFilter` の後ろ）に置いている。`@Component` にしない（2回出るため）
- CSP：本番用の HTML（`npm run build`）にだけ `<meta>` で入れる（`vite.config.ts`）。開発中の Vite には入れない。見た目だけ `'unsafe-inline'`（ドラッグ＆ドロップのライブラリが `<style>` を足すため）。API の返事にも `default-src 'none'; frame-ancestors 'none'`
- AWS のときにすること：画面の HTML に `frame-ancestors 'none'`（`<meta>` では効かない）と HSTS を、配信する側のヘッダーで付ける
- 確かめ方：`npm run build` → `npm run preview`（5173。開発中の Vite を止めてから）。プレビューでも `/api` は Spring Boot へ転送される

**要件定義書の見直し（#55）で決めたこと**
- 版を付けた（今は 1.9）。改訂履歴（10章）は Git の履歴から、要件が変わったものだけをまとめた。本文の「2026-11-01 に追加（イシュー #49）」のような書き込みは履歴へ移した。7章・8章の「→ 〇〇で対応（日付）」は残している
- 前提条件と制約は 1.1。章の番号は変えていない（ほかの設計書が「要件 3.7」「5章」のように番号で参照しているため）
- 非機能要件の ID は節ごとの英字：NA（全般）・NS（セキュリティ）・NP（性能）・NO（運用）・NT（テスト）
- 受け入れ基準は 9章（AC-01〜AC-59）。公開の前の手動のテスト（NT-03）の項目にそのまま使う。要件を足したら、受け入れ基準と改訂履歴も足す
- 作ったら、スキル `quality-check`（差分だけ）で確かめてから PR を作る

**登録の回数制限（#53）で決めたこと**（詳細は `detailed-design.md` 3.8.5・3.8.8、`api-design.md` A-01）
- ゲストの作成とは別に数える（種類 `REGISTRATION`。Flyway V5 で `rate_limit_events.kind` の CHECK 制約を作り直した）。上限は1時間に10回（`app.rate-limit.registration`）
- 数えるのは、登録できたときと、登録済み（409）で断ったとき。409 も数えるのは、メールアドレスが使われているかを次々に調べられないようにするため。入力の誤り（400）は数えない。制限中は、登録済みかどうかを確かめずに 429
- 409 でも記録を残すため、登録は `TransactionTemplate` で書き、中では null を返して外で 409 にする（退会と同じ形）
- 前からの漏れ：要件 5章のログ（ログインの成功・失敗、登録）を出していなかった。成功はユーザー ID、失敗は IP アドレスで出す（メールアドレスは出さない）。ゲストログインも出す
- 品質チェックで直したこと：ログが DB の確定の前に出ていたので、ゲストとログインも `TransactionTemplate` にし、確定したあとで出すようにした（ログインの `noRollbackFor` はやめた）
- 確認の注意：開発中は接続元がいつも自分のパソコンなので、登録を何度も試すと自分も1時間止められる。戻すときは `rate_limit_events` を消す（回数制限（#41）の項）。Claude が実行すると許可で止められることがあるので、そのときは私が PowerShell で実行する

**複数ボード（#51）で決めたこと**（詳細は `detailed-design.md` 3.8.10・4.2、`api-design.md` 5.2、`screen-design.md` W-11）
- ボード一覧の画面（W-11）を `/` に作り、ボードは `/boards/{ID}` で開く。ログイン・登録・ゲストのあとは一覧へ。1人10個まで（ゲストも）。並びは作った順（`id` の順。テーブルに列は足していない）
- 新しいボードは「未着手／作業中／完了」の3リスト入り（登録と同じ `BoardService.createWithInitialColumns`）。作ったらそのボードを開く
- ラベルはユーザー単位のまま、全ボードで共通。ボードを消してもラベルは残る
- 削除はボード画面のヘッダーの「…」（スマートフォンは ☰）。最後の1つも消せる（一覧に「ボードがありません」）。消したら一覧へ戻り、緑の通知
- API は `/api/board` をやめて `/api/boards/{id}`。リストの追加は `POST /api/boards/{id}/columns`。カードは同じボードのリストへだけ動かせる（別のボードは 404）
- テーブルは `boards.user_id` の一意制約を外しただけ（Flyway V4）。今あるボードはそのまま
- 鍵：リスト・カードの操作は、そのもののボードに鍵（`findWithLockByColumnIdAndUserId` など。副問い合わせ付きの `@Query` に `@Lock`）。ボードの作成は、ユーザーの行に鍵（`UserRepository.findWithLockById`）をかけてから数える
- 画面：`BoardRoute` が URL の ID を確かめ（数字でない・大きすぎるなら 404 の画面）、`BoardProvider` に `key` を付けてボードごとに作り直す。`BoardHeader` は一覧でも使うので、名前の変更と削除は `BoardPage` から `onRename`・`onDeleteBoard` で渡す
- テストの手助け `TestBoards.firstBoardId`・`firstBoardPath`（一覧の1つ目＝登録したときのボード）
- 品質チェックで直したこと：`/boards/大きすぎる数字` で「読み込めませんでした」が出ていた（`Number.isSafeInteger`）、詳細設計書の重複した1行。モックアップは1ボードのまま（見た目の手本のため）

**メールアドレス・パスワードの変更（#49）で決めたこと**（詳細は `detailed-design.md` 3.8.9、`api-design.md` A-20・A-21）
- ▾ と ☰ のメニューから、別々のモーダル（W-09・W-10）。本人確認は今のパスワード（確認メールは送らない）。パスワード違いはログインの失敗と一緒に数える
- 変えたら、ほかのブラウザ・端末のセッションは消し、今のセッションは ID を新しくして残す。ほかの端末は「変える前のメールアドレス」で探す（Spring Session は持ち主をメールアドレスで覚えている）。メールアドレスを変えたら、今のセッションの持ち主の名前も新しいアドレスになる
- 照合は退会と共通（`AuthService.findIfPasswordMatches`）。登録済みかどうかは、パスワードが合ってから確かめる
- 見つけて直した不具合：変更のすぐあとの PUT・POST が 403 になる（新しい CSRF トークンが次のリクエストまで渡されない）。変更の返事ですぐ渡す（`issueCsrfToken`。テストは `CsrfCookieTest`）
- 通知に成功の種類を足した（`showToast(メッセージ, 'success')`。緑の「✓」）。W-08〜W-10 の見た目は `AccountModal.module.css` で共通
- 品質チェックで直したこと：登録画面がメールアドレスの前後の空白を除かずに送り、サーバーで形式の誤りになっていた（前からの不具合）

**ハンバーガーメニュー（#47）で決めたこと**（詳細は `screen-design.md` W-03、`detailed-design.md` 4.6）
- ☰ に入れるのは「ラベル管理」とアカウント（メールアドレス・ログアウト・退会）。検索欄と「絞り込み」はよく使うのでヘッダーに残す
- 開き方は、ほかのメニューと同じくボタンの下に小さく開く（`shared/ui/Menu.module.css`）
- パソコン用と ☰ を両方置き、CSS の `@media` で片方を隠す。右側の箱は `display: contents` にして、検索欄を `order` で2段目へ

**検索・絞り込み（#45）で決めたこと**（詳細は `requirements.md` 3.8、`detailed-design.md` 4.8）
- サーバーは変えず、画面にあるボードのデータで絞り込む。条件は URL（`/?q=…&label=…&due=…&importance=…&urgency=…`）だけに持つ
- 対象はタイトルと説明文。`normalize('NFKC')`・小文字・カタカナ→ひらがなでそろえる。空白で区切ると全部の言葉
- 同じ種類の中は「どれか」、種類どうしは「全部」。期限の区切りは C-05 の色分けと同じ
- 合わないカードは隠し、絞り込み中はカードのドラッグと追加を止める（隠れたカードがあると、並び順の位置が決まらないため）。リストの並び替えはできる
- 小窓はスマートフォンでは画面の下から出す（ヘッダーが折り返して高さが変わるため）
- 品質チェックで直したこと：削除したラベルが条件に残る不具合（`withKnownLabels`）、読み上げ用の見えない文字 `sr-only`（`global.css`。`<span>` の `aria-label` は使わない）、絞り込みを始めたらカードの追加欄を閉じる
- 確認の注意：Chrome を自動で動かしてゲストを何度も作ると、ゲスト作成の回数制限（1時間に10回）にかかる。そのときは `rate_limit_events` を消す（回数制限（#41）の項）

**退会（#43）で決めたこと**（詳細は `detailed-design.md` 3.8.7）
- 退会のパスワード違いは、ログインの失敗と一緒に数える（`LOGIN_FAILURE`）。空のパスワードは数えない
- 退会したら、その人のセッションを全部消す（Spring Session の `findByPrincipalName`。持ち主の名前はメールアドレス）。ほかの端末では、次の操作でログイン画面へ移る
- 見つけて直した前からの不具合：操作中のセッション切れで、ログイン画面に「ログインの有効期限が切れました」が出ていなかった。`navigate` と `setUser(null)` を `startTransition` で囲んで、一緒に反映させた（`detailed-design.md` 4.2）
- `FormField` は `ComponentProps<'input'>` にして、`ref` を受け取れるようにした（エラーのあとにパスワード欄へカーソルを置くため）
- 品質チェックで直したこと：退会のセッション削除は、ユーザーの削除を確定したあと（`TransactionTemplate`。Spring Session の削除は別のトランザクションで動くため）。押すと開く部品（リストの「…」・ヘッダーのメニュー・ラベルの一覧）から `role="menu"` をやめ、`aria-expanded`・`aria-controls` で開け閉めを伝える形にした（矢印キーで動かないのに「メニュー」と読み上げられていたため）。ラベルの一覧は `<fieldset>` と `<legend>`、付いているかは `aria-pressed`。メニューの見た目は `shared/ui/Menu.module.css` にまとめた

**回数制限（#41）で決めたこと**（詳細は `detailed-design.md` 3.8.8、テーブルは `database-design.md` 3.9）
- 回数は DB の `rate_limit_events`（Flyway V3）に1回1行で記録し、直近15分（ゲストは1時間）の行を数える。古い行は `RateLimitCleanupJob` が毎時消す
- 制限中は正しいパスワードでも 429。ログインに成功しても失敗の回数は消さない。429 には `Retry-After`（秒）を付ける。ログは上限に達したときに1回だけ。設定（`app.rate-limit`）は `@Validated` で、書き忘れると起動しない
- 同時に送られても上限を超えないように、「種類:IPアドレス」に PostgreSQL のアドバイザリーロックをかけてから数える。ログインは失敗の記録を取り消さないように、中では例外を投げない（#53 で `noRollbackFor` から `TransactionTemplate` に変えた）
- IP アドレスは `getRemoteAddr()`。AWS では `server.forward-headers-strategy: native` で中継役（ロードバランサー）の後ろの本当の接続元を取る（まだ入れていない）
- テストの注意：MockMvc の接続元は、すべてのテストでリクエストごとに違う IP アドレスにしている（`TestcontainersConfiguration`）。回数制限のテスト（`RateLimitApiTest`）は上限を3回にしている（10回だと DB の接続の数と同じで、鍵がなくても同時のテストが通ってしまうため）
- 開発中は接続元がいつも自分のパソコン（`0:0:0:0:0:0:0:1`）なので、確かめると自分も止められる。戻すときは `docker exec taskboard-db-1 psql -U task_app_user -d task_app -c "DELETE FROM rate_limit_events;"`

**GitHub Actions（#39）で決めたこと**（詳細は `detailed-design.md` 5.3）
- 走らせるのは、PR（作ったとき・push したとき）と main への反映。PR を作る前のブランチへの push では走らない。要件 5章もこれに合わせて直した
- PR を作ると GitHub Actions が走る。PR のページで `backend`・`frontend` が緑になるのを確かめてから、私にマージを頼む（`gh pr checks 番号` でも見られる）
- `.github/workflows/` を変えた push は、Git Credential Manager のログインでは断られる。`gh` のログインで送る（`CLAUDE.md`）

**ゲストログイン（#37）で決めたこと**（詳細は `detailed-design.md` 3.8.4〜3.8.6）
- ゲストの作成の回数制限は、ログイン失敗の回数制限と一緒に、あとで別のイシューで作る（AWS の前に必ず）
- サンプルの期限の「今日」は日本の日付で数える（サーバーは UTC で動くため）
- 見つけて直した前からの不具合：ログアウトのあと、再読み込みせずにログインし直すと 403 になっていた。ログアウトの返事で新しい CSRF トークンを渡すようにした
- テストの注意：MockMvc の `with(csrf())` を使うと、同じアプリを使い回すあとのテストで CSRF の Cookie が返らなくなる。Cookie を確かめるテストは `CsrfCookieTest`（新しく起動したアプリで、`with(csrf())` を使わない）

**ドラッグ＆ドロップ（#35）で決めたこと**（詳細は `detailed-design.md` 3.8.2・4.5）
- ドロップしたら先に画面を変えて API を呼び、失敗したら `restored` で動かす前に戻す。保存を待つ間は次のドラッグをさせない
- つかむところは、カードはタイトルのボタン（カード全体に広がっている）、リストは名前のボタン。Enter・クリックはこれまでどおり、スペースキーでつかむ。見出し全体をつかむところにすると、ボタンの入ったボタンになるのでやめた
- 読み上げの文言は日本語（`features/board/dragAndDrop.ts`）。ライブラリは CSS の `gap` を数えないので、間は `margin` で空ける
- リストとカードの追加・削除・並び替えは、最初にボードの行に鍵をかける（`BoardRepository.findWithLockByUserId`）。2つのタブから同時に操作しても、番号が重ならず上限も超えない

**ラベル管理（#33）で決めたこと**
- 色の選択肢は HTML のラジオボタン（`ColorSwatches`）。見た目だけを色の四角にする（矢印キーで選べ、読み上げでも伝わる）
- ラベル管理はカード詳細の上に重ねて開ける。削除したラベルは、カード詳細の下書きからも外して保存する
- `Modal` の Esc は `<dialog>` の `cancel` で受け取る（カーソルがどこにあっても、いちばん上のモーダルに届く）

**品質チェック（講義 初級編16）で行ったこと**（2026-10-31）
- #27：チェックの道具を入れた（Checkstyle・SpotBugs・oxlint の規則・Prettier）
- #29：品質チェックの手順をスキル `quality-check` にした
- #31：見つかった指摘をすべて直し、規則を「エラー」に戻した。主なもの：500 にならない例外の変換（`ResponseEntityExceptionHandler`）、同時の登録の 409、`show-sql` を開発だけに、空白を除いて送る、URL の後ろの日本語をリンクにしない、モーダルを `<dialog>` に、カードを `<button>` に

## 作り方の要点（実装で決めたこと）
- **サーバー**（`backend/`、パッケージ `io.github.keiwebcreate.taskboard`）
  - Controller → Service → Repository。1件を探すときは必ず「ID ＋ 持ち主」のメソッド（`findByIdAndColumnBoardUserId` など）。他人のデータは 404
  - エラーは独自の例外を投げ、`common/error/ApiExceptionHandler` が Problem Details（＋`code`）にする。Spring Security の 401・403 もここに回している
  - ログインは自作の API（`AuthService`）。セッションは Spring Session JDBC（30分）。CSRF は `csrf.spa()` ＋ `CsrfCookieFilter`（GET でも `XSRF-TOKEN` の Cookie を返す）
  - 上限は `application.yml` の `app.limits` → `LimitsProperties`。並び順の振り直しは `common/position/Positions.renumber`。並び順や数を変える処理は、最初に `findWithLockByUserId` でボードに鍵をかける
  - テーブルは Flyway（V1〜V5）だけが作る。JPA は `ddl-auto: validate`。`labels.color` は CHAR(7) なので `@JdbcTypeCode(SqlTypes.CHAR)` が必要
  - API のテストは `@SpringBootTest` ＋ MockMvc ＋ Testcontainers。テストごとに `UUID` 入りのメールアドレスで登録する。API で作れないデータ（ラベルなど）や position の確認は `JdbcTemplate`
- **画面**（`frontend/src/`）
  - `api/client.ts` の `apiFetch` を必ず通す（CSRF ヘッダー、`ApiError`、401 でログイン画面へ）
  - ボードのデータは `BoardContext` の `boardReducer` だけで変える。API が成功してから、返ってきたデータで action を送る（ドラッグ＆ドロップだけは先に送り、失敗したら `restored` で戻す）
  - 共通の部品は `shared/ui/`（`Modal`・`ConfirmDialog`・`InlineForm`・`InlineEdit`・`LinkifiedText`・`ToastProvider`）。共通の見た目（ボタン・入力欄）は `styles/global.css`
  - 一度に開くその場の入力欄は1つだけ（`BoardPage` の `openForm`）
  - モーダルは `shared/ui/Modal`（HTML の `<dialog>` を `showModal()`）。Esc は `cancel` で受け取り、Tab はモーダルの中だけを回す。開いたときのカーソルの位置は `data-autofocus`（`autoFocus` は使わない）
  - 押せるものは `<button>`。中にリンクがあって全体をボタンにできないとき（カード）は、見えないボタンを全体に広げる。メニュー・一覧の Esc は `useEscapeKey`
  - チェックは oxlint・Prettier。規則はすべて「エラー」（指摘が1件でも失敗）

## 開発環境（Windows 11）
| もの | 状態 |
|---|---|
| JDK | Temurin 25.0.4（JAVA_HOME も 25）。Temurin 21 も残っている（使わなくなったら削除してよい） |
| Node.js ／ npm、Git、VS Code、`gh` | 入っている。`gh` は keiwebcreate でログイン済み |
| Docker | 開発用 DB（`compose.yaml`）とテストの Testcontainers で使う |
| 開発用 DB | Docker の PostgreSQL 18（プロジェクト名 `taskboard`・コンテナ `taskboard-db-1`・ボリューム `taskboard_db-data`）。5432、DB `task_app`、ユーザー `task_app_user` |
| DB のパスワード | Windows のユーザー環境変数 `DB_PASSWORD`（中身は私だけが知っている）。コードや Git には書かない |
| AWS CLI ／ Terraform ／ jq | 2.39.3 ／ 1.16.6 ／ 1.8.2（2026-11-03 に winget で導入）。AWS CLI の初期のリージョンは `ap-southeast-2`、認証は `C:\Users\imhun\.aws\` |
| SSH の鍵 | `C:\Users\imhun\.ssh\taskboard_ec2`（EC2 用。パスフレーズ付き）。前からある `id_rsa` は別の用途 |
| 使わないもの | Windows の PostgreSQL 18（サービス `postgresql-x64-18`）と MySQL 8.4（`MySQL84`・3307）は停止して残してある。削除するかは後で決める |

- **品質チェックはスキル `quality-check` の手順で**（自動チェック ＋ AI の見直し。PR の前は差分だけで、公式のスキル `security-review` も走らせる。デプロイの前は「デプロイ前のセキュリティチェック」）
- **サーバーの起動はスキル `dev-servers` の手順で**（`.claude/skills/dev-servers/SKILL.md`）。ポートは 5173・8080・5432 から変えない。DB → Spring Boot → Vite の順
  - ポートを使っているプロセスを Claude が止めようとすると、Claude Code の許可で止められることがある。そのときは私に止めてもらう
- ポート 3306 は別プロジェクト（cytech_step9_app）の Docker MySQL。触らないこと
- Git Bash で `docker exec` にコンテナ内のパスを渡すときは `MSYS_NO_PATHCONV=1` が必要
- **画面の動きを自動で確かめる方法**：スクラッチパッド（チャットごとの作業用フォルダ）に `puppeteer-core` を入れ、パソコンの Chrome（`C:/Program Files/Google/Chrome/Application/chrome.exe`）を画面なしで動かした。プロジェクトには入れていない。新しいチャットでは、スクラッチパッドに入れ直す。`demo` のデータを変えないように、毎回新しいユーザーを登録して試す（開発用の DB に `label-（数字）@example.com` などが残っている）
  - 作り直すときのコツ（2026-11-01）：登録・ログインのあとは一覧（`/`。タイルは `main ul li a`）なので、ボードを開いてから操作する。ボード名は `header h1 button`。カードは `main article`、カードのタイトルは `article p button`、ドラッグのライブラリの印は `data-rfd-…`（`rbd` ではない）。メニューは `aria-controls` の先の要素（`role="menu"` は使っていない）。「▾」のボタンは文字の「▾」で探す（「絞り込み」ボタンにも `aria-controls` があるため）。ゲストを何度も作るときは、先に `rate_limit_events` を消す
- サーバーのコードを変えたら、Spring Boot を起動し直す（Vite は自動で読み込むが、Spring Boot は読み込まない）。`bootRun` は開発用の設定（プロファイル `dev`）で起動し、SQL をログに出す
- 2026-10-25 に、OneDrive のファイルロックで Gradle が失敗したため、フォルダを OneDrive の外（`C:\dev\Raise_Tech`）に移した。OneDrive の中に開発フォルダを置かないこと

## Git・GitHub
- リモート：`https://github.com/keiwebcreate/raise-tech-beginner-edition`（公開）。About 欄に説明あり
- 運用：イシュー → `種類/番号-内容` のブランチ → PR（`Closes #番号`）→ 私が Squash and merge。マージしたブランチは GitHub で自動削除
- `main` はルールセット「main を保護」で守っている（PR 必須・承認0人・GitHub Actions の `backend`・`frontend` の成功が必須・強制プッシュと削除の禁止・管理者も例外なし）。この引き継ぎ書の更新も PR で行う
- これまでのイシューと PR：#1〜#74（イシューと PR は番号を共有している）。#73（AWS 段階4）の PR が #74
- パソコンに、マージ済みのローカルブランチが残っている（`chore/1-github-workflow` など）。消すのは任意（`git branch -D`）。Claude がブランチやファイルを消そうとすると、Claude Code の許可で止められることがある
- `backend/HELP.md`（Spring Initializr が作った説明）がパソコンに残っている。Git の対象外なので、そのままでよい
