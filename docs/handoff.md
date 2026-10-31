# 引き継ぎ書（2026-10-31 更新）

前のチャットの続きです。以下を読んでから作業を再開してください。

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

## 今の状態（2026-10-31）

**できていること**（講義の CRUD はカードとリストについてひととおり完了）

| 機能 | API | 画面 |
|---|---|---|
| 登録・ログイン・ログアウト・自分 | A-01・A-02・A-04・A-05 | W-01 ログイン、W-02 登録、ヘッダーのメールアドレスとログアウト |
| ボードの取得・ボード名の変更 | A-06・A-07 | W-03 ボード（ボード名はクリックで変更） |
| リストの追加・名前の変更・削除 | A-08・A-09・A-10 | 「＋ リストを追加」、名前をクリックで変更、「…」メニューから削除 |
| カードの追加・保存・削除 | A-12・A-13・A-14 | 「＋ カードを追加」、W-04 カード詳細（全項目の保存・削除） |
| ラベルの付け外し | A-13 の `labelIds` | カード詳細の「＋」（今あるラベルだけ） |

- テスト：サーバー 50件（`./gradlew test`）、画面 33件（`npm test`）。すべて成功
- 開発用の DB に確認用のユーザー **`demo@example.com` ／ `password123`** がいる。ラベル（仕事・買い物・あとで）は SQL で入れたもの（ラベルを作る画面がまだないため）

**まだないもの**

| 機能 | 要件・設計 | メモ |
|---|---|---|
| **ラベル管理**（追加・変更・削除） | T-01・T-03、W-05、A-16〜A-18 | **次にやると決定**。入口はヘッダーの「ラベル管理」と、カード詳細のラベル一覧の下の「ラベルを管理」 |
| ドラッグ＆ドロップ（並び替え・移動） | L-05・C-04、A-11・A-15 | @hello-pangea/dnd。並び順の振り直しの部品 `Positions.renumber` はもうある |
| ゲストログイン・24時間後の削除 | U-06〜U-08、A-03 | サンプルデータはモックアップのゲスト用と同じ。ログイン画面の「ゲストとして試す」もこのとき |
| 退会 | U-11、W-08、A-19 | ヘッダーのメニュー（▾）もこのとき |
| ログイン失敗の回数制限 | U-10 | 方式が未決（`detailed-design.md` 7章） |
| `/api/auth/me` でユーザーが消えていたとき | `detailed-design.md` 3.8.6 | ゲストの削除を作るときに足す |
| GitHub Actions（自動テスト） | 要件 5章 | 作ったら main のルールセットに「テストの成功が必須」を足す |
| AWS へのデプロイ | G-04 | 構成が未決。画面と API は同じドメインにする（CORS を設定しないため） |
| 見直し（急がない） | | 要件：改訂履歴・前提条件と制約・非機能要件の ID・受け入れ基準。テーブル：論理名と物理名・CRUD 表 |

## 次にやること
**ラベル管理（W-05・A-16 追加・A-17 変更・A-18 削除）**。イシューの案を作って見せるところから。
- 使い回せる部品：`Modal`・`ConfirmDialog`・`useToast`・`useOutsideClick`・`InlineForm` の考え方、サーバーの `LimitsProperties`（ラベル30個）・`LimitExceededException`
- 新しく要るもの：`LabelNameDuplicatedException`（409 LABEL_NAME_DUPLICATED）、8色のチェック（`LabelColor`）、`boardReducer` の `labelAdded`・`labelUpdated`・`labelDeleted`（`labelDeleted` はカードの `labelIds` からも外す）
- 講義の次の回の内容も確かめてから決める

## 作り方の要点（実装で決めたこと）
- **サーバー**（`backend/`、パッケージ `io.github.keiwebcreate.taskboard`）
  - Controller → Service → Repository。1件を探すときは必ず「ID ＋ 持ち主」のメソッド（`findByIdAndColumnBoardUserId` など）。他人のデータは 404
  - エラーは独自の例外を投げ、`common/error/ApiExceptionHandler` が Problem Details（＋`code`）にする。Spring Security の 401・403 もここに回している
  - ログインは自作の API（`AuthService`）。セッションは Spring Session JDBC（30分）。CSRF は `csrf.spa()` ＋ `CsrfCookieFilter`（GET でも `XSRF-TOKEN` の Cookie を返す）
  - 上限は `application.yml` の `app.limits` → `LimitsProperties`。並び順の振り直しは `common/position/Positions.renumber`
  - テーブルは Flyway（V1・V2）だけが作る。JPA は `ddl-auto: validate`。`labels.color` は CHAR(7) なので `@JdbcTypeCode(SqlTypes.CHAR)` が必要
  - API のテストは `@SpringBootTest` ＋ MockMvc ＋ Testcontainers。テストごとに `UUID` 入りのメールアドレスで登録する。API で作れないデータ（ラベルなど）や position の確認は `JdbcTemplate`
- **画面**（`frontend/src/`）
  - `api/client.ts` の `apiFetch` を必ず通す（CSRF ヘッダー、`ApiError`、401 でログイン画面へ）
  - ボードのデータは `BoardContext` の `boardReducer` だけで変える。API が成功してから、返ってきたデータで action を送る
  - 共通の部品は `shared/ui/`（`Modal`・`ConfirmDialog`・`InlineForm`・`InlineEdit`・`LinkifiedText`・`ToastProvider`）。共通の見た目（ボタン・入力欄）は `styles/global.css`
  - 一度に開くその場の入力欄は1つだけ（`BoardPage` の `openForm`）
  - リンターは oxlint（Vite のひな形のまま。ESLint から変更）

## 開発環境（Windows 11）
| もの | 状態 |
|---|---|
| JDK | Temurin 25.0.4（JAVA_HOME も 25）。Temurin 21 も残っている（使わなくなったら削除してよい） |
| Node.js ／ npm、Git、VS Code、`gh` | 入っている。`gh` は keiwebcreate でログイン済み |
| Docker | 開発用 DB（`compose.yaml`）とテストの Testcontainers で使う |
| 開発用 DB | Docker の PostgreSQL 18（プロジェクト名 `taskboard`・コンテナ `taskboard-db-1`・ボリューム `taskboard_db-data`）。5432、DB `task_app`、ユーザー `task_app_user` |
| DB のパスワード | Windows のユーザー環境変数 `DB_PASSWORD`（中身は私だけが知っている）。コードや Git には書かない |
| 使わないもの | Windows の PostgreSQL 18（サービス `postgresql-x64-18`）と MySQL 8.4（`MySQL84`・3307）は停止して残してある。削除するかは後で決める |

- **サーバーの起動はスキル `dev-servers` の手順で**（`.claude/skills/dev-servers/SKILL.md`）。ポートは 5173・8080・5432 から変えない。DB → Spring Boot → Vite の順
  - ポートを使っているプロセスを Claude が止めようとすると、Claude Code の許可で止められることがある。そのときは私に止めてもらう
- ポート 3306 は別プロジェクト（cytech_step9_app）の Docker MySQL。触らないこと
- Git Bash で `docker exec` にコンテナ内のパスを渡すときは `MSYS_NO_PATHCONV=1` が必要
- 2026-10-25 に、OneDrive のファイルロックで Gradle が失敗したため、フォルダを OneDrive の外（`C:\dev\Raise_Tech`）に移した。OneDrive の中に開発フォルダを置かないこと

## Git・GitHub
- リモート：`https://github.com/keiwebcreate/raise-tech-beginner-edition`（公開）。About 欄に説明あり
- 運用：イシュー → `種類/番号-内容` のブランチ → PR（`Closes #番号`）→ 私が Squash and merge。マージしたブランチは GitHub で自動削除
- `main` はルールセット「main を保護」で守っている（PR 必須・承認0人・強制プッシュと削除の禁止・管理者も例外なし）。この引き継ぎ書の更新も PR で行う
- これまでのイシューと PR：#1〜#26（イシューと PR は番号を共有している）。#26（この引き継ぎ書の更新）のマージ後は、開いているイシューはない
- パソコンに、マージ済みのローカルブランチが残っている（`chore/1-github-workflow` など）。消すのは任意（`git branch -D`）
