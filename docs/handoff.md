# 引き継ぎ書（2026-10-31 更新）

前のチャットの続きです。以下を読んでから作業を再開してください。

**最初にすること**
1. PR #40（GitHub Actions）がマージ済みかを `gh pr view 40` で確かめる。マージ済みなら `main` に移って `git pull`。まだなら、私にマージを頼む
2. 下の「次にやること」から、次に作るものを私と決める（講義のアプリ側は、品質チェックの回で終わっている）

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

## 今の状態（2026-10-31）

**できていること**（講義の CRUD はカード・リスト・ラベルについてひととおり完了。ドラッグ＆ドロップも完了）

| 機能 | API | 画面 |
|---|---|---|
| 登録・ログイン・ログアウト・自分 | A-01・A-02・A-04・A-05 | W-01 ログイン、W-02 登録、ヘッダーのメールアドレスとログアウト |
| ボードの取得・ボード名の変更 | A-06・A-07 | W-03 ボード（ボード名はクリックで変更） |
| リストの追加・名前の変更・削除 | A-08・A-09・A-10 | 「＋ リストを追加」、名前をクリックで変更、「…」メニューから削除 |
| カードの追加・保存・削除 | A-12・A-13・A-14 | 「＋ カードを追加」、W-04 カード詳細（全項目の保存・削除） |
| ラベルの付け外し | A-13 の `labelIds` | カード詳細の「＋」 |
| ラベル管理（追加・変更・削除） | A-16・A-17・A-18 | W-05。ヘッダーの「ラベル管理」と、カード詳細のラベル一覧の下の「ラベルを管理」（カード詳細の上に重ねて開く） |
| ドラッグ＆ドロップ（リストの並び替え、カードの並び替え・移動） | A-11・A-15 | W-03。リストは名前を、カードはカード全体をつかむ。キーボードでも動かせる |
| ゲストログイン・ログアウトでの削除・24時間後の削除 | A-03・A-04・A-05 | W-01 の「ゲストとして試す」、W-03 の「ゲスト」の札と注意書きの帯 |
| GitHub Actions（自動テスト） | 要件 5章 | `.github/workflows/ci.yml`。PR と main への反映で `backend`・`frontend` が走り、成功しないとマージできない（`detailed-design.md` 5.3） |

- テスト：サーバー 98件（`./gradlew test`）、画面 45件（`npm test`）。すべて成功
- コードのチェック（#27）：サーバーは Checkstyle・SpotBugs（`./gradlew check`）、画面は oxlint の規則を足し、Prettier を入れた（`npm run lint`・`npm run format:check`）。道具と規則は `detailed-design.md` 5.2
- 開発用の DB に確認用のユーザー **`demo@example.com` ／ `password123`** がいる。ラベル（仕事・買い物・あとで）は最初に SQL で入れたもの

**まだないもの**

| 機能 | 要件・設計 | メモ |
|---|---|---|
| 退会 | U-11、W-08、A-19 | ヘッダーのメニュー（▾）もこのとき |
| ログイン失敗・ゲスト作成の回数制限 | U-10 | 方式が未決（`detailed-design.md` 7章）。**AWS で公開する前に必ず作る**（ゲストを無制限に作れるため） |
| AWS へのデプロイ | G-04 | 構成が未決。画面と API は同じドメインにする（CORS を設定しないため） |
| 見直し（急がない） | | 要件：改訂履歴・前提条件と制約・非機能要件の ID・受け入れ基準。テーブル：論理名と物理名・CRUD 表 |

## 次にやること
講義のアプリ側は、品質チェックの回（初級編16）で終わっている。残りの機能から、私と相談して決める。候補：
- **退会**（U-11、W-08、A-19）。ヘッダーのメニュー（▾）も。ゲストには出さない
- ログイン失敗・ゲスト作成の回数制限（U-10）。AWS の前に必ず
- AWS へのデプロイ（G-04）
- 作ったら、スキル `quality-check`（差分だけ）で確かめてから PR を作る

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
  - テーブルは Flyway（V1・V2）だけが作る。JPA は `ddl-auto: validate`。`labels.color` は CHAR(7) なので `@JdbcTypeCode(SqlTypes.CHAR)` が必要
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
| 使わないもの | Windows の PostgreSQL 18（サービス `postgresql-x64-18`）と MySQL 8.4（`MySQL84`・3307）は停止して残してある。削除するかは後で決める |

- **品質チェックはスキル `quality-check` の手順で**（自動チェック ＋ AI の見直し。PR の前は差分だけ）
- **サーバーの起動はスキル `dev-servers` の手順で**（`.claude/skills/dev-servers/SKILL.md`）。ポートは 5173・8080・5432 から変えない。DB → Spring Boot → Vite の順
  - ポートを使っているプロセスを Claude が止めようとすると、Claude Code の許可で止められることがある。そのときは私に止めてもらう
- ポート 3306 は別プロジェクト（cytech_step9_app）の Docker MySQL。触らないこと
- Git Bash で `docker exec` にコンテナ内のパスを渡すときは `MSYS_NO_PATHCONV=1` が必要
- **画面の動きを自動で確かめる方法**：スクラッチパッド（チャットごとの作業用フォルダ）に `puppeteer-core` を入れ、パソコンの Chrome（`C:/Program Files/Google/Chrome/Application/chrome.exe`）を画面なしで動かした。プロジェクトには入れていない。新しいチャットでは、スクラッチパッドに入れ直す。`demo` のデータを変えないように、毎回新しいユーザーを登録して試す（開発用の DB に `label-（数字）@example.com` などが残っている）
- サーバーのコードを変えたら、Spring Boot を起動し直す（Vite は自動で読み込むが、Spring Boot は読み込まない）。`bootRun` は開発用の設定（プロファイル `dev`）で起動し、SQL をログに出す
- 2026-10-25 に、OneDrive のファイルロックで Gradle が失敗したため、フォルダを OneDrive の外（`C:\dev\Raise_Tech`）に移した。OneDrive の中に開発フォルダを置かないこと

## Git・GitHub
- リモート：`https://github.com/keiwebcreate/raise-tech-beginner-edition`（公開）。About 欄に説明あり
- 運用：イシュー → `種類/番号-内容` のブランチ → PR（`Closes #番号`）→ 私が Squash and merge。マージしたブランチは GitHub で自動削除
- `main` はルールセット「main を保護」で守っている（PR 必須・承認0人・GitHub Actions の `backend`・`frontend` の成功が必須・強制プッシュと削除の禁止・管理者も例外なし）。この引き継ぎ書の更新も PR で行う
- これまでのイシューと PR：#1〜#40（イシューと PR は番号を共有している）。#39（GitHub Actions）の PR が #40
- パソコンに、マージ済みのローカルブランチが残っている（`chore/1-github-workflow` など）。消すのは任意（`git branch -D`）。Claude がブランチやファイルを消そうとすると、Claude Code の許可で止められることがある
- `backend/HELP.md`（Spring Initializr が作った説明）がパソコンに残っている。Git の対象外なので、そのままでよい
