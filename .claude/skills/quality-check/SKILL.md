---
name: quality-check
description: TaskBoard の品質チェック（自動チェック ＋ AI によるコードの見直し）の手順と観点。「品質チェックして」「コードを見直して」「レビューして」と頼まれたとき、PR を作る前、機能を作り終えたときに使う。講義 初級編16「AIを使った品質チェック方法」の手順をこのプロジェクト向けにしたもの。
---

# 品質チェック

コードを**変えずに読むだけ**で確かめ、決まった形で報告する。直すのは、報告を見たユーザーと相談して決めてから。コマンドは `beginner_version` フォルダを基準にする。

## 0. 範囲を決める

ユーザーの頼み方で決める。はっきりしなければ、PR の前なら「差分だけ」、それ以外は「全体」。

| 範囲 | 読むもの |
|---|---|
| 全体 | `backend/src`・`frontend/src` のすべて |
| 差分だけ | `git diff main...HEAD` と `git status` に出るファイル。変わった部分を使う側・呼ばれる側も必要に応じて読む |
| インフラ | `terraform/`・`deploy/`・`scripts/`・`backend/src/main/resources/application-prod.yml`（AWS の構成と、EC2 に置く設定。2026-11-04 に追加、イシュー #71）。「差分だけ」でも、これらが変わっていればインフラの自動チェックと観点も使う |

## 1. 自動チェック

すべて実行して、成功・失敗と、指摘の件数・場所を集める。サーバーのテストは Docker（Testcontainers）が要る。

| 場所 | コマンド | 中身 | 結果の場所 |
|---|---|---|---|
| サーバー | `.\gradlew.bat check`（`backend`） | テスト、Checkstyle、SpotBugs | `backend/build/reports/checkstyle/`・`spotbugs/`・`tests/` |
| 画面 | `npm run lint`（`frontend`） | oxlint | 画面に出る |
| 画面 | `npm run format:check` | Prettier | 画面に出る |
| 画面 | `npm test` | Vitest | 画面に出る |
| 画面 | `npm run build` | 型チェック（`tsc -b`）と本番用の作成 | 画面に出る |
| インフラ | `terraform fmt -check -recursive`（`terraform`） | Terraform の書き方がそろっているか | 画面に出る（ずれたファイルの名前） |
| インフラ | `terraform validate`（`terraform`） | Terraform の書き方の誤り | 画面に出る |
| インフラ | `terraform plan -detailed-exitcode`（`terraform`） | ファイルと AWS の実物がずれていないか。終了コード 0 はずれなし、2 は差分あり（報告する。勝手に `apply` しない）。AWS に何も作っていないとき（`destroy` のあと）は飛ばす | 画面に出る |
| インフラ | `trivy config --skip-files "**/*.tfplan" --ignorefile terraform/.trivyignore terraform`（`beginner_version` で） | Terraform の設定ミス（入口が広い・暗号化していないなど）。承知のうえで外したものは `terraform/.trivyignore`（理由つき。2026-11-04 に6件）。外したものも含めて見るときは `--ignorefile` を外す | 画面に出る（表）。件数は `--format json` |

- 範囲が「差分だけ」（PR の前）のときは、Claude Code の公式のスキル **`security-review`** も走らせる（Skill ツールで `security-review`。今のブランチの、まだ `main` に入っていない変更をセキュリティの観点で見直す。2026-11-01 に追加、イシュー #63）。範囲が「全体」のときは使えない（差分だけを見るため）ので、下の「デプロイ前のセキュリティチェック」の手順にする
- 「警告」も数えて報告する（失敗にならないだけで、指摘であることは同じ）
- 道具と規則の決めごとは `docs/detailed-design.md` 5.2。規則を外したい指摘があっても、勝手に設定を変えない（報告して相談する）

## 2. AI の見直し

設計書（`docs/requirements.md`・`api-design.md`・`screen-design.md`・`detailed-design.md`）と `CLAUDE.md` のルールを基準に、コードを読む。次の観点は毎回必ず見る。

**サーバー（Spring Boot）**
- 所有者の確認：1件を探すときに「ID ＋ 持ち主」のメソッドを使っているか。他人のデータは 404 か
- 例外：想定外の入力（URL の ID に数字以外、JSON の形の違い、メソッドの違い）が 400・404・405 などになり、500 にならないか。Problem Details に `code` が付くか
- 同時の操作：「あるか確かめてから保存」の間に割り込まれたとき（一意制約の違反）に 500 にならないか
- `@Transactional`：変更する Service のメソッドに付いているか。読むだけなら `readOnly = true`
- 設定：開発だけのはずの設定（`show-sql` など）が本番にも効いていないか。秘密情報が書かれていないか
- ログとレスポンス：パスワード・メールアドレス・エラーの詳細が出ていないか
- null：null かもしれない戻り値（Spring 7 は `@Nullable` で示している）を確かめずに使っていないか

**画面（React）**
- フック：依存の漏れ・余分。`boardReducer` 以外でボードのデータを変えていないか
- アクセシビリティ：モーダル（Tab でフォーカスが後ろに抜けないか）、押せる要素はボタンか（Enter・Space で動くか）、入力欄に名前（label・aria-label）があるか
- API：`apiFetch` を通しているか。API が成功してから画面を変えているか
- 文言：メッセージが `screen-design.md` 7章のとおりか

**インフラ（Terraform・Nginx・スクリプト）**（設計の正は `docs/deployment.md`）
- 入口：セキュリティグループの入口が必要なポート・必要な相手だけか（`0.0.0.0/0` で開けていないか）。Spring Boot（8080）と RDS（5432）が外から届かないか
- DB：`publicly_accessible = false`・プライベートサブネット・暗号化。RDS の入口が EC2 のセキュリティグループからだけか
- 秘密の値：パスワード・鍵が Git・Terraform の state・`output`・ログ・コマンドの引数に出ていないか（パスワードは Secrets Manager かパラメータストア。Terraform では `manage_master_user_password`・`ephemeral` ＋ `value_wo`）。`git ls-files terraform` に state・`tfvars`・鍵・`tfplan` がないか
- IAM：EC2 のロールの権限が、必要な操作・必要なものだけか（`*` を使っていないか）
- 料金：月 20 USD の上限（`deployment.md` 3.1）。NAT ゲートウェイ・ロードバランサー・予備の DB など、高いものを足していないか。足すなら料金を報告する
- `plan`：思わぬ変更（`~`）・作り直し（`-/+`）・削除（`-`）がないか。とくに EC2 の `user_data` の変化（作り直しや起動し直しで IP アドレスが変わる）
- 改行：`.tf`・`.sh`・`deploy/` が LF か（CRLF だと `user_data` が変わったと判定される・スクリプトが動かない。`.gitattributes`）
- Nginx：`/api/` の転送で `X-Forwarded-*` をブラウザの値のまま渡していないか（接続元の偽装。詳細設計書 3.8.8）。画面のファイルにセキュリティのヘッダー（`taskboard-headers.conf`）が付くか。`location` に `add_header` を書くと外側のものが消える点
- スクリプト：`set -euo pipefail`、変数を `"` で囲んでいるか、`source` するファイルの値（スペースを含む値は `"` で囲む）、何度実行してもよい作りか
- systemd：root で動かしていないか、`NoNewPrivileges` などの制限

**画面とサーバーの食い違い**
- 入力チェックのルール（必須・文字数・形式）が両方で同じか
- 前後の空白：画面で取り除いて確かめた値と、送っている値が同じか（サーバーは受け取った値で数える）
- 上限（`LIMITS` と `application.yml` の `app.limits`）、ラベルの8色が同じか

**日本語ならではの点**
- URL のリンク化で、後ろに続く日本語までリンクにしていないか
- Enter で送る入力欄で、変換中（`isComposing`）は送らないか
- 文字数の数え方（絵文字などの扱い）が画面・サーバー・DB で食い違っていないか

**標準から外れていないか**
- Spring Boot・React で一般的な書き方があるのに、自作していないか（例：例外の変換は `ResponseEntityExceptionHandler`、モーダルは `<dialog>`）

## 3. 裏付け

疑わしい点は、報告の前に確かめる。推測のまま「不具合」と書かない。

- ライブラリの動きは、Gradle のキャッシュにある jar を `javap` で調べる（例：その例外が `ErrorResponse` を実装しているか）。`find ~/.gradle/caches -name "spring-web-*.jar"`
- 画面のロジックは、作業用のフォルダ（スクラッチパッド）で小さく試す
- 確かめられなかったものは、報告で「未確認」と書く

## 4. 報告

次の形で出す。専門用語には短い説明を添える。

1. **結論**（1〜2文。大きな問題があるか）。よくできている点も短く書く
2. **自動チェックの結果**：コマンドごとに成功・失敗、指摘の件数
3. **指摘の表**：次の4つに分け、それぞれ「#・場所・内容・直し方」の表にする。番号は通し番号
   - 不具合（実際に困ることがあるもの）
   - セキュリティ（`security-review` の指摘と、2 の見直しで見つけたセキュリティの問題。どの要件 NS-xx に当たるかを書く）
   - 標準から外れているもの
   - チェックの道具（道具が出した指摘、道具や規則の不足）
   - 細かいもの（古いコメント、不要なファイルなど）
4. **質問**：どこまで直すかを、おすすめと選択肢を示して1つ聞く

## デプロイ前のセキュリティチェック

AWS へ公開する前に1回（と、大きな変更のあとで公開し直す前に）行う。`security-review` は差分だけを見るので、これまでのコード全体はこの手順で見る。

1. **コードの全体の見直し**：範囲を「全体」にして、上の 1〜4 を行う。2 の見直しは、要件定義書 5.2 の NS-01〜NS-14（OWASP Top 10 をもとにした対策の一覧）を1行ずつ基準にし、「どこで守っているか（ファイル・設定）」を表にする。守れていないものは「セキュリティ」の指摘にする
2. **ライブラリ**：Dependabot の通知が0件か確かめる（`gh api "repos/keiwebcreate/raise-tech-beginner-edition/dependabot/alerts?state=open" --jq length`）。Dependabot の PR が残っていれば、先に片付ける
3. **インフラの全体の見直し**：範囲を「インフラ」にして、上の 1〜4 を行う（Trivy と、2 の「インフラ」の観点）
4. **公開した環境**：AWS の設定（DB をインターネットに出さない・HTTPS・秘密情報の置き場所・画面のヘッダー）は、ファイルを読むだけでは確かめきれないので、公開したあとで受け入れ基準 AC-47〜AC-54 を確かめる（要件定義書 9.6）
5. 報告は 4 の形。1〜4 のどれで見つけたかも書く

- AI の見直しは、よくある間違いを見つけるためのもので、専門家の侵入テスト（実際に攻撃してみるテスト）の代わりにはならない。ポートフォリオの公開としては、この手順で十分とする（2026-11-01 にユーザーと決めた）

## 5. 直すとき

- 直すのは、ユーザーが範囲を決めてから。いつもの「イシュー → ブランチ → PR」の流れで進める（`CLAUDE.md`）
- 指摘が多いときは、イシューを分ける案を出す（例：道具の追加と、指摘の修正）
- 直したら 1 の自動チェックをもう一度すべて通し、PR の「確認したこと」に結果を書く

## つまずいた点のメモ

- **CSRF の Cookie のテストが、ときどきだけ失敗する**（#37）：MockMvc の `with(csrf())` は、CSRF の確認をする部品（アプリ全体で1つ）のトークンの保存先を、Cookie を使わないテスト用のものに差し替えたままにする。同じアプリを使い回すテストの順番しだいで、Cookie が返らなくなる。Cookie を確かめるテストは `CsrfCookieTest`（`@DirtiesContext` で新しく起動し、`with(csrf())` を使わない）に書く
- **Prettier でほぼ全部のファイルが「違う」と出る**：改行の形が原因。この PC の Git（`core.autocrlf=true`）が CRLF に変えている。`.prettierrc.json` の `endOfLine: auto` で対応済み。消さないこと
- **規則は全部「エラー」**：#27 で道具を入れたときは、今のコードに出た指摘の規則を一時的に「警告」にしたが、#31 で直して元に戻した。新しく規則を足すときも、指摘が残るなら同じように一時的に警告にして、すぐ直すイシューを作る
- **oxlint の誤判定**：`react/memo-dependencies` は、`try … finally` の `finally` の中で使った値を「余分」と判定する。`finally` を使わない書き方にする（`detailed-design.md` 5.2）。道具の指摘が怪しいときは、スクラッチパッドで小さな例を作り、`npx oxlint -c .oxlintrc.json <フォルダ>` で試して確かめる
- **SpotBugs で調べない指摘**（`backend/config/spotbugs/exclude.xml`）：`EI_EXPOSE_REP`・`EI_EXPOSE_REP2`（record の List と DI のコンストラクタがすべて当てはまる）、`VA_FORMAT_STRING_USES_NEWLINE`（テストの JSON の `\n`）
- **oxlint で使わない規則**：`react/react-in-jsx-scope`（React 17 より前の規則）、`import/no-unassigned-import`（CSS の import を違反にする）
- **SpotBugs の結果**は HTML だけに出る。件数は `build/reports/spotbugs/main.html`・`test.html` の「Total Warnings」
- **Trivy が古い `plan` のファイルを読む**（#71）：`terraform/` に `plan -out` で保存した `*.tfplan` があると、Trivy は `.tf` ではなくそちらを調べる。`--skip-files "**/*.tfplan"` を付ける。`apply` に使った `*.tfplan` は、終わったら消す（Git には入らない設定）
- **`plan` で、変えていない EC2 の `user_data` が変わったと出る**（#67）：`.tf` の改行が CRLF になっている（このパソコンの Git は `core.autocrlf=true`）。`terraform/.gitattributes` で LF に固定してある。そのまま `apply` しない
- **パソコンでサーバーのテストが全部失敗する（`Could not connect to Ryuk`・`Connection reset`）**（#69）：Docker Desktop の、パソコンとコンテナの間の通信の不調。2026-11-04 に WSL が更新されたあとに起きた。Docker Desktop の起動し直しでは直らなかった。パソコンを再起動する。GitHub Actions のテストでも確かめられる
- **Gradle の非推奨の警告**（`Configuration.setVisible`）は SpotBugs のプラグインから出ている。Gradle 11 で問題になるので、プラグインを上げるときに確かめる
