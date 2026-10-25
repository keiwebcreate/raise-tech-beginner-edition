# 引き継ぎ書（2026-10-25 更新）

前のチャットの続きです。以下を読んでから作業を再開してください。

**最初に読むファイル**（すべて `beginner_version/` の中）
1. `docs/handoff.md`（このファイル）
2. `CLAUDE.md`：AI が守るルール（進め方・コードの書き方・コマンド）
3. `docs/detailed-design.md`：詳細設計書（ひな形作りと実装の中心）
4. 必要に応じて：`docs/requirements.md`（要件）、`docs/database-design.md`（テーブル。SQLは10章）、`docs/api-design.md`（API）、`docs/screen-design.md`（画面）、`mockup/`（動くモックアップ）

## プロジェクト
- 講座の課題で、トレロ風のタスク管理アプリ「TaskBoard」（仮）を作っている。作業フォルダは `beginner_version/`。完成したらポートフォリオとして公開する
- 講座の目的は、AIと相談しながら「要件定義 → 設計 → 環境構築 → 実装 → テスト → デプロイ」を一通り経験すること。締め切りはない
- 技術スタックは講座の指定で、**Java 25（最新のLTS）** / Spring Boot、React（Vite・TypeScript）、**PostgreSQL 18**、AWS
  - 2026-10-25 に訂正：DBは MySQL ではなく PostgreSQL、Java は 21 ではなく最新の LTS（＝25）だった
- 私（ユーザー）はWeb制作の経験はあるが、プログラミングは未経験。コードは基本的にAIに書いてもらう。ただし自分で説明できるように、何をしているかを解説してほしい

## 進め方の希望
- 決めごとは、講義動画と同じく **質問を1つずつ出し、おすすめと選択肢を示す形式** で進めてほしい
- 決まったことは、毎回「ここまでの決定」としてまとめてほしい
- 勉強のため、あえて難しいほうを選ぶことがある

## 開発環境（Windows 11）
| もの | 状態 |
|---|---|
| Node.js v26 / npm、Git、VS Code | インストール済み |
| Docker | 入っている（別プロジェクトでも使用）。テストの Testcontainers で使う。PostgreSQL 18 のイメージは取得済み |
| JDK | Temurin 25.0.4（LTS）をインストール済み。JAVA_HOME も 25 を指している。Temurin 21 も残っている（使わなくなったら削除してよい） |
| PostgreSQL 18 | 18.6 を Windows に直接インストール済み（サービス `postgresql-x64-18`）。ポート 5432、DB `task_app`（UTF8・照合順序 Japanese_Japan.932）、アプリ用ユーザー `task_app_user` を作成済み。パスワードは私だけが知っている。コードやGitには書かず、環境変数で渡すこと |
| MySQL 8.4.9 | 使わない（サービス `MySQL84`・ポート3307）。停止して「手動」にしてある。PostgreSQL でアプリが動いたら削除するか決める |

- **注意**：ポート3306は別プロジェクト（cytech_step9_app）の Docker MySQL が使っている。触らないこと
- Git Bash で `docker exec` にコンテナ内のパスを渡すときは `MSYS_NO_PATHCONV=1` が必要（パスが Windows 形式に変換されるため）

## 完了したこと（すべて 2026-10-25）

| # | 工程 | 成果物 | 要点 |
|---|---|---|---|
| 1 | 要件定義 | `docs/requirements.md` | ボードは1人1つ。リストの追加・名前変更・削除・並び替え。カードはタイトル・説明・重要度・緊急度・期限・ラベル。ラベルは自由に管理。メール＋パスワードでログイン、ゲストあり、退会あり（U-11）。上限はリスト20／カード100／ラベル30。非機能要件・テスト方針は5章 |
| 2 | テーブル設計 | `docs/database-design.md` | PostgreSQL 18。主キーは IDENTITY、並び順は0からの連番、削除は ON DELETE CASCADE、日時は TIMESTAMPTZ、更新日時はトリガー。SQLは Docker の PostgreSQL 18 で流して確認済み |
| 3 | 画面設計 | `docs/screen-design.md`・`docs/wireframes.html` | シンプルな業務ツール風。メッセージ一覧は7章、ラベルの8色は6章 |
| 4 | API設計 | `docs/api-design.md` | REST形式。`GET /api/board` 1回でまとめて取得。エラーは Problem Details＋`code`。CSRF は Cookie `XSRF-TOKEN` → ヘッダー `X-XSRF-TOKEN` |
| 5 | モックアップ | `mockup/`（開き方は `mockup/README.md`） | HTML・CSS・JS で動く試作品。私が触って「今の形でOK」。見つかった動きは設計書に反映済み（要件 C-09：カードに説明を3行表示、など） |
| 6 | 詳細設計 | `docs/detailed-design.md`・`CLAUDE.md` | 下の表 |
| 7 | DB・Java の訂正 | 各設計書 | MySQL → PostgreSQL 18、Java 21 → 25 |

詳細設計の決定（くわしくは `detailed-design.md` 1章）：
- サーバー：Controller → Service → Repository の3層、パッケージは機能ごと（`io.github.keiwebcreate.taskboard`）、DTO は record、エンティティは子→親の `@ManyToOne` だけ（ボード画面はSQL 3回）、所有者の確認は「ID＋持ち主」で探す Repository のメソッド、エラーは独自の例外＋`@RestControllerAdvice`、セッションは Spring Session JDBC（PostgreSQL）、Gradle、Lombok なし、テストDBは Testcontainers
- 画面：TypeScript、`useReducer`＋Context、CSS Modules、フォルダは機能ごと（`features/`・`shared/`）、React Router、fetch を包んだ自作の関数、ドラッグ＆ドロップは @hello-pangea/dnd
- ゲストのサンプルデータはモックアップと同じ中身

## 次にやること

### 1. Java 25 を入れる（私が操作する）
1. https://adoptium.net/ から **Temurin 25（LTS）の Windows x64 の .msi** をダウンロードして実行する
2. インストールの途中の「Set JAVA_HOME variable」を **「Will be installed on local hard drive」** にする（JAVA_HOME を 25 に切り替える）
3. VS Code とターミナルを開き直し、`java -version` で `25` と出ればOK
- Temurin 21 は残しておいてよい（使わなくなったら削除してよい）
- 注意：Java 25 を使うには **Gradle 9.1 以上**、**Spring Boot は Java 25 に対応した版** が必要。ひな形を作るときに確かめる

### 2. PostgreSQL 18 を入れる（私が操作する）
1. https://www.postgresql.org/download/windows/ の「Download the installer」から 18.x の Windows x86-64 版を入れる。PostgreSQL Server・pgAdmin 4・Command Line Tools にチェック、**Stack Builder は外す**。管理者（postgres）のパスワードを決めて控える。ポートは 5432、Locale は Default のまま
2. 「SQL Shell (psql)」を開き（Enter で初期値のまま進み、管理者のパスワードを入れる）、次を実行する
   ```sql
   CREATE USER task_app_user WITH PASSWORD '自分で決めたパスワード';
   CREATE DATABASE task_app OWNER task_app_user;
   ```
   `\l task_app` で Encoding が UTF8 なら完成。`\q` で終わる
3. 管理者の PowerShell で MySQL を止める
   ```powershell
   Stop-Service MySQL84
   Set-Service MySQL84 -StartupType Manual
   ```

### 3. Spring Boot と React のひな形を作る（AI）
- `backend/`・`frontend/` を作る（詳細設計書 2章・3.1・4.1、`CLAUDE.md` のコマンド）
- Flyway の V1（テーブル。`database-design.md` 10章）・V2（Spring Session のテーブル）を、ローカルの PostgreSQL で流して確かめる
- バージョンは、そのときの最新の安定版（Java 25 に対応しているもの）にする

### 4. その後
- 実装（API → 画面の順を想定）、テスト、GitHub Actions
- 未決事項：AWSの構成、回数制限の実装方法（`api-design.md` 8章・`detailed-design.md` 7章）

### 見直しで残っている項目（急がない）
- ⚪ 要件：改訂履歴、前提条件と制約、非機能要件のID、受け入れ基準
- ⚪ テーブル：論理名と物理名、CRUD表

## Git
- `beginner_version/` が Git リポジトリ。ブランチは `main`、リモートは `https://github.com/keiwebcreate/raise-tech-beginner-edition.git`
- `beginner_version/CLAUDE.md` に「add・commit・push・ブランチ操作は承認なしで許可」と書いてある
- 2026-10-25 時点で、設計書・モックアップ・CLAUDE.md はすべてコミットして GitHub にプッシュ済み
