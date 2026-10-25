# 引き継ぎ書（2026-10-25 更新）

前のチャットの続きです。以下を読んでから作業を再開してください。

## ⚠ 場所の移動（2026-10-25）
- OneDrive がビルドのファイルをつかんで Gradle が失敗する（`Failed to clean up stale outputs`・`Unable to delete directory ...build\test-results\test\binary`）ため、`Raise_Tech` フォルダごと **OneDrive の外の `C:\dev\Raise_Tech`** に移すと決めた（移すのは私が操作する）
- 移動は済み（2026-10-25）。移した直後は `build` の中のフォルダに読み取り専用の印が付いていて Gradle が失敗したため、`attrib -R "build\*" /S /D` で外した。その後 `.\gradlew.bat test` で全部のテスト（`TaskBoardApplicationTests` 1件・`UserRepositoryTest` 3件）が通ることを確認
- Docker の DB（`compose.yaml`）はプロジェクト名 `taskboard` を固定しているので、フォルダを移してもデータ（ボリューム `taskboard_db-data`）はそのまま使える

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
| Docker | 入っている（別プロジェクトでも使用）。**開発用DB**（`compose.yaml`）とテストの Testcontainers で使う |
| JDK | Temurin 25.0.4（LTS）をインストール済み。JAVA_HOME も 25 を指している。Temurin 21 も残っている（使わなくなったら削除してよい） |
| PostgreSQL 18（Docker） | **開発用DBはこれ**。`beginner_version/compose.yaml`（プロジェクト名 `taskboard`・コンテナ `taskboard-db-1`・ボリューム `taskboard_db-data`）。ポート 5432、DB `task_app`（UTF8）、ユーザー `task_app_user`。`beginner_version` で `docker compose up -d` で起動、`docker compose stop` で停止 |
| DB のパスワード | Windows のユーザー環境変数 `DB_PASSWORD` に登録済み（中身は私だけが知っている）。`compose.yaml` と Spring Boot の両方がこれを読む。コードやGitには書かない。VS Code のターミナルで使うには、登録後に VS Code を開き直す |
| PostgreSQL 18（Windows） | 使わない。講座で Docker を使うとわかったため。18.6 を直接インストールしたが、停止して「手動」にしてある（サービス `postgresql-x64-18`。psql・pgAdmin は Docker 版を見るのにも使える）。削除するかは後で決める |
| MySQL 8.4.9 | 使わない（サービス `MySQL84`・ポート3307）。停止して「手動」にしてある。削除するかは後で決める |

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

### 済んだこと（2026-10-25）
- Java 25・PostgreSQL 18（のちに Docker に変更）の準備、MySQL の停止
- Spring Boot のひな形（`backend/`）：Spring Boot 4.1.2・Gradle 9.8.2（Groovy 形式）。起動クラス `TaskBoardApplication`（UTC で動かす）。テストの PostgreSQL は `postgres:18` に固定
- `application.yml`：DB の接続先だけ（`DB_URL`・`DB_USERNAME` は省略時ローカル用の初期値、`DB_PASSWORD` は環境変数のみ）。Docker の DB につながることを確認済み
- 進め方の希望：**Spring Boot のセットアップは最小限で、少しずつ進める**

### 3. 続き（AI）
- 済み：Flyway の V1（`database-design.md` 10章の SQL と同じ）を置き、起動時に Docker の DB に6テーブル＋トリガー5つができることを確認
- 済み：講義は JPA の ddl-auto: update でテーブルを作るが、このプロジェクトは **Flyway のまま＋JPA は ddl-auto: validate で確かめる** と決定（2026-10-25）。`user/User`（エンティティ）・`UserRepository`（findByEmail・existsByEmail）を作り、`open-in-view: false`・`show-sql: true` を設定。起動時の validate が通ることを確認
- 済み：`UserRepositoryTest`（`@DataJpaTest`＋Testcontainers）を追加。3つとも成功し、`show-sql` で JPA が作った insert・select がログに出ることを確認（講義の「ログで確かめる」にあたる）。`TestcontainersConfiguration` は public にした
- 済み：`C:\dev\Raise_Tech` へ移した後、全部のテストをまとめて実行して成功
- 次の一歩の候補：V2（Spring Session のテーブル。今は無いので、起動中に毎分 `spring_session does not exist` のエラーがログに出るが、V2 を置けば消える）、React（`frontend/`）のひな形
- バージョンは、そのときの最新の安定版にする

### 4. その後
- 実装（API → 画面の順を想定）、テスト、GitHub Actions
- 未決事項：AWSの構成、回数制限の実装方法（`api-design.md` 8章・`detailed-design.md` 7章）

### 見直しで残っている項目（急がない）
- ⚪ 要件：改訂履歴、前提条件と制約、非機能要件のID、受け入れ基準
- ⚪ テーブル：論理名と物理名、CRUD表

## Git
- `beginner_version/` が Git リポジトリ。ブランチは `main`、リモートは `https://github.com/keiwebcreate/raise-tech-beginner-edition.git`
- `beginner_version/CLAUDE.md` に「add・commit・push・ブランチ操作は承認なしで許可」と書いてある
- 2026-10-25 時点で、設計書・モックアップ・CLAUDE.md・backend のひな形・compose.yaml はすべてコミットして GitHub にプッシュ済み
