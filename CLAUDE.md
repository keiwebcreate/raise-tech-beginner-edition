##Git操作
-add、commit、push、ブランチを切る、切り替える操作については承認なしで許可します。

## このプロジェクト

講座の課題で作るトレロ風のタスク管理アプリ「TaskBoard」（仮）。完成したらポートフォリオとして公開する。

- 作業を始める前に `docs/handoff.md`（引き継ぎ書）を読む
- 設計書はすべて `docs/` にある。作り方の中心は `docs/detailed-design.md`（詳細設計書）
  - 要件：`requirements.md`／画面：`screen-design.md`・`wireframes.html`／テーブル：`database-design.md`／API：`api-design.md`
- `mockup/` は動きを確かめるためのモックアップ。本番では使わない（見た目と部品の分け方の参考にする）

## 進め方

- 私（ユーザー）はWeb制作の経験はあるが、プログラミングは未経験。コードは基本的にAIが書く
- 私が自分で説明できるように、何をしているかを日本語で解説しながら進める
- 決めることは **質問を1つずつ出し、おすすめと選択肢を示す形式** で聞く。決まったら「ここまでの決定」としてまとめる
- 設計書と違う作り方をしたくなったら、先に相談する。決まったら設計書も直す

## フォルダとコマンド

| フォルダ | 中身 | 起動 | テスト |
|---|---|---|---|
| `backend/` | Spring Boot（Java 21・Gradle） | `./gradlew bootRun` | `./gradlew test` |
| `frontend/` | React（Vite・TypeScript） | `npm run dev` | `npm test` |
| `mockup/` | モックアップ | `npx serve mockup`（`beginner_version` で実行） | なし |

- 開発中は `http://localhost:5173` を開く。`/api` は Vite が Spring Boot（8080）へ転送する
- ローカルの MySQL はポート **3307**、DB は `task_app`。**3306 は別のプロジェクトの Docker なので触らない**
- DBのパスワードなどの秘密情報は、コードにもGitにも書かない。環境変数（`DB_URL`・`DB_USERNAME`・`DB_PASSWORD`）で渡す

## サーバー（Spring Boot）のルール

- パッケージは `io.github.keiwebcreate.taskboard`。機能ごと（`auth`・`user`・`board`・`column`・`card`・`label`）と `common` に分ける
- Controller → Service → Repository の3層。Controller に判断を書かない。変更する Service のメソッドには `@Transactional`
- APIのJSONは DTO（`record`）で受け取り・返す。エンティティをそのまま返さない
- エンティティは子→親の `@ManyToOne(fetch = LAZY)` だけ。`@OneToMany` と JPA の `cascade` は使わない（削除は DB の `ON DELETE CASCADE`）
- setter は作らず、変えてよい項目だけを変えるメソッドを作る。Lombok は使わない
- **所有者の確認**：1件を探すときは、必ず持ち主の条件が入ったメソッド（`findByIdAndColumnBoardUserId` など）を使う。`findById` で取ってから比べる書き方はしない。見つからなければ `NotFoundException`（404）
- ルール違反は独自の例外を投げ、`ApiExceptionHandler` で Problem Details に変換する。Controller に try-catch を書かない
- テーブルの変更は Flyway の SQL ファイルを追加して行う。すでにあるファイルは書き換えない
- エラーの詳細・パスワード・トークン・メールアドレスをログやレスポンスに出さない

## 画面（React）のルール

- TypeScript で書く。`any` は使わない。APIの型は `src/api/types.ts`
- フォルダは `features/`（機能ごと）と `shared/`（共通）。部品と CSS Modules（`Xxx.module.css`）を同じ場所に置く。色などは `styles/variables.css` の CSS 変数を使う
- APIは必ず `src/api/client.ts` を通して呼ぶ（CSRF のヘッダーとエラーの変換をここで行う）
- ボードのデータは `BoardContext` の `boardReducer` だけで変える。画面だけの状態は部品の `useState`
- `dangerouslySetInnerHTML` は使わない
- Enter で送信する入力欄では、日本語の変換中（`isComposing`）は送信しない
- 画面の文言・メッセージは `screen-design.md` 7章のとおりにする

## テスト

- サーバーは JUnit（DBは Testcontainers の MySQL）。所有者の確認・並び順・上限・入力チェック・ログインを中心に書く
- 画面は Vitest で、ロジックのある部分（期限の表示、URLのリンク化、入力チェック、`boardReducer`）だけ
- 変更したら、関係するテストを実行して結果を報告する
