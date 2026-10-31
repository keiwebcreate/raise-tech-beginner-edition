# 引き継ぎ書（2026-10-31 更新）

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
- 済み：講義と同じく、Docker の DB に `docker exec ... psql` で直接 SQL を打って確認。`users` に登録・検索・更新・削除ができ、更新日時のトリガーと ON DELETE CASCADE（ユーザーを消すとボードも消える）が動くことを確認。試したデータは消してある（ID は1から使ったので、次は2から始まる）
- 済み：Flyway の V2（`V2__create_session_tables.sql`。Spring Session JDBC 4.1.1 の `schema-postgresql.sql` と同じ）を置いた。テストが通り、起動時に Docker の DB が version 2 になって `spring_session`・`spring_session_attributes` ができ、毎分のエラーが消えたことを確認
- 済み（2026-10-31）：講義（初級編11）に合わせて GitHub の運用ルールを決めた（下の「Git」）。イシュー #1
- 済み（2026-10-31）：**API実装①（Read）**（イシュー #3）。講義は先に Read の API を少し作る。このプロジェクトの Read（`GET /api/board`・`GET /api/auth/me`）はログインが前提なので、**案A** に決定：Board・BoardColumn・Card（＋Level）・Label のエンティティと Repository、DTO、`NotFoundException`、`BoardService.getBoard(userId)` まで作り、`BoardServiceTest`（テストデータは `src/test/resources/board/board-test-data.sql`）4件で確かめた。SQL は4回で済むことをログで確認。Controller はログインを作るときにつなぎ、そのとき API に通信して確かめる
  - `labels.color` は CHAR(7) なので、`Label` に `@JdbcTypeCode(SqlTypes.CHAR)` が必要だった（ないと validate で止まる）
- 講義の次の回は「React でのデータ表示」。本物の API はまだ呼べない（Controller がなく、Spring Security の設定もないので全部 401）ため、**偽の API（MSW）** でボードのデータを返して画面を作ると決定（2026-10-31）。作業は2つのイシューに分けた
  - 済み（2026-10-31）：**① フロントエンドの環境**（イシュー #5）。`frontend/` を create-vite 9.3.3（React 19・TypeScript 7・Vite 8）で作成。Vitest 5 を追加（`npm test` ＝ `vitest run`）。`/api` は 8080 へ転送。`styles/variables.css`・`global.css` はモックアップから移した。リンターはひな形の **oxlint** のまま（設計書の ESLint から変更）
  - 済み（2026-10-31）：**② ボード画面にデータを表示する**（イシュー #7）：`api/types.ts`・`client.ts`（`ApiError`・CSRF ヘッダー。401 の処理はログインのとき）・`boardApi`、MSW 3（`src/mocks/`。開発中だけ動く。サンプルはモックアップのゲスト用と同じ中身）、`BoardContext`・`BoardProvider`＋`boardReducer`（`loaded` だけ）、React Router 8（`/` と 404）、`BoardPage`・`BoardHeader`・`Column`・`CardItem`・`CardDescription`・`LabelChip`（表示だけ）、`shared/lib` の `due`・`linkify`・`constants`。Vitest 14件
  - 共通のボタン（`.btn`・`.btn-primary`）と `.muted` は `styles/global.css` に置いた（どの画面でも使うため）
- 済み（2026-10-31）：**サーバーのポートのルール**（イシュー #9。講義の指示）：ポートは 5173・8080・5432 から変えず、使われていたら止めてから起動する（何が使っていても止めてよい、と決定）。Vite に `strictPort: true`、スキル `dev-servers`（`beginner_version/.claude/skills/`）、`CLAUDE.md` に記載
- 済み（2026-10-31）：**CORS は設定しない**と `api-design.md` 2.3 に明記（同じオリジンのため。本番も同じドメインにする）
- 講義（初級編12の後半）は「画面に出ているのが本当に DB のデータか、SQL で1〜2件足して確かめる」。MSW のままでは確かめられないので、① サーバーのログイン＋`BoardController`（API で確かめる）→ ② 画面のログイン＋MSW を外す（画面で確かめる）の2段階にすると決定（2026-10-31）
  - 済み（2026-10-31）：**① サーバーのログイン**（イシュー #11）：`SecurityConfig`（`csrf.spa()`＋全レスポンスで XSRF-TOKEN を返す `CsrfCookieFilter`、401・403 は `ApiExceptionHandler` に回す）、`ApiExceptionHandler`、`AuthService`・`AuthController`（A-01 登録・A-02 ログイン・A-04 ログアウト・A-05 自分）、`BoardController`（A-06）、セッション30分。`AuthControllerTest` 12件。起動したサーバーに Vite 経由で登録し、SQL で足したカード2枚が `GET /api/board` で返ることを確認
  - 開発用の DB に確認用のユーザー **`demo@example.com` ／ `password123`** がいる（「未着手」に SQL で足したカードが2枚）
  - まだないもの：ゲスト（A-03）・退会（A-19）・回数制限、`/api/auth/me` でユーザーが消えていたときの処理（ゲストの削除を作るときに足す）
  - 済み（2026-10-31）：**② 画面のログイン（W-01・W-02）＋ MSW を外す**（イシュー #15）：`AuthContext`・`AuthProvider`（起動時に `/api/auth/me`、401 UNAUTHENTICATED でセッション切れのお知らせを付けて `/login` へ）、`RequireLogin`・`GuestOnly`（画面の出し分け）、`LoginPage`・`SignupPage`・`FormField`、`authApi`、`shared/lib/validation.ts`、ヘッダーにメールアドレスとログアウト。MSW は `src/mocks`・`public/mockServiceWorker.js`・パッケージごと外した。Vitest 24件
  - ヘッダーのメニュー（▾ と「退会する」）、ログイン画面の「ゲストとして試す」は、退会・ゲストを作るときに足す
  - 入力欄（`.field`・`.input`・`.error` など）とボタンの種類（`.btn-block`・`.btn-ghost`）も `styles/global.css` に置いた
- 済み（2026-10-31）：**Create：カードとリストの追加**（イシュー #19。講義の Create）
  - サーバー：A-08 `POST /api/columns`（`ColumnService`・`ColumnController`）、A-12 `POST /api/columns/{id}/cards`（`CardService`・`CardController`。持ち主の条件付きの `findByIdAndBoardUserId` で探す）、`LimitsProperties`（`app.limits`。起動クラスに `@ConfigurationPropertiesScan`）、`LimitExceededException`（409）。`CreateColumnAndCardApiTest` 9件（上限を小さくする設定で確かめる）。サーバーのテストは全部で29件
  - 画面：`InlineForm`（その場の入力欄。Enter で追加して続けて入力、Esc で閉じる、変換中の Enter は無視）、`AddCardForm`・`AddColumnForm`、通知（`ToastProvider`・`useToast`）、`boardReducer` に `columnAdded`・`cardAdded`。一度に開く入力欄は1つ（`BoardPage` の `openForm`）。画面のテストは全部で28件
- 済み（2026-10-31）：**Update：カード詳細・リスト名・ボード名の変更**（イシュー #21。講義の Update。Delete は次の回）
  - サーバー：A-13 `PUT /api/cards/{id}`（`Card.update`。ラベルは `findAllByIdInAndUserId` で自分のものだけを取り、数が足りなければ 404。説明が空なら null）、A-09 `PATCH /api/columns/{id}`、A-07 `PATCH /api/board`。`UpdateApiTest` 10件（ラベルは API がまだないので JdbcTemplate で入れる）。サーバーのテストは全部で39件
  - 画面：`Modal`（重ねて開ける。Esc は一番上だけ）・`ConfirmDialog`（W-06）・`InlineEdit`（その場での名前の変更）・`LinkifiedText`、`CardModal`（W-04。下書きを編集して「保存」でまとめて送る。変更があれば閉じる前に確認）、`LabelPicker`（今あるラベルの付け外し。「ラベルを管理」はまだ）、`boardReducer` に `cardSaved`・`columnRenamed`・`boardRenamed`。画面のテストは全部で31件
  - 確認用に、`demo@example.com` にラベル（仕事・買い物・あとで）を SQL で足した
- 済み（2026-10-31）：**Delete：カードとリストの削除**（イシュー #23。講義の Delete）
  - サーバー：並び順の振り直しの共通部品 `common/position`（`Positioned`・`Positions.renumber`。`Card`・`BoardColumn` が実装。ドラッグ＆ドロップでも使う）、A-14 `DELETE /api/cards/{id}`、A-10 `DELETE /api/columns/{id}`（中のカードは DB の CASCADE で消える）。`PositionsTest` 4件（DB を使わない単体テスト）・`DeleteApiTest` 7件（position は JdbcTemplate で DB を直接見て確かめる）。サーバーのテストは全部で50件
  - 画面：カード詳細の「カードを削除」、リストの「…」メニュー（`ColumnMenu`）→「リストを削除」、どちらも `ConfirmDialog` で確認。`useOutsideClick`（メニューや一覧の外のクリックで閉じる。`LabelPicker` も使う）、`boardReducer` に `cardDeleted`・`columnDeleted`。画面のテストは全部で33件
  - 次は **ラベル管理（W-05・A-16〜A-18）** と決定（2026-10-31）
- 済み（2026-10-31）：**README**（イシュー #17。講義の指示）。読み手はポートフォリオを見る人＋手元で動かしたい人。AI を使っていることは一文だけ。`frontend/README.md`（Vite のひな形の説明）は消した。GitHub の About 欄にも説明を入れた。機能を作ったら README の「進み具合」も直す。スクリーンショットは見た目がそろってから足す
- 済み（2026-10-31）：**使っているバージョンの一覧**を `detailed-design.md` 5.1 にまとめた（イシュー #13）。要件定義書 6章からも参照。バージョンを上げたら 5.1 も直す
  - その後：ログイン → MSW を外して本物の `GET /api/board` につなぐ
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
- 2026-10-31 から **イシュー → ブランチ → PR → 私が GitHub の画面でマージ** の流れで進める（くわしくは `CLAUDE.md` の「GitHub の運用ルール」）。GitHub の操作は `gh` コマンドで行う
  - ブランチ名は `種類/イシュー番号-内容`、マージは Squash and merge だけ、マージしたブランチは自動で削除
  - `main` はルールセット「main を保護」で守っている：PR 必須（承認0人）、強制プッシュと削除の禁止、管理者も例外なし
  - テストの成功を必須にする設定は、GitHub Actions を作るときに足す
