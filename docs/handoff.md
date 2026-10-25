# 引き継ぎ書（2026-10-25 更新）

前のチャットの続きです。以下を読んでから作業を再開してください。

**最初に読むファイル**（すべて `beginner_version/docs/`）
1. `handoff.md`（このファイル）
2. `requirements.md`：要件定義書
3. `database-design.md`：テーブル設計書（ER図は2章）
4. `screen-design.md`：画面設計書（画面遷移図は3章）
5. `api-design.md`：API設計書
6. `wireframes.html`：ワイヤーフレーム（ブラウザで開く。中身は読まなくても `screen-design.md` で足りる）

## プロジェクト
- 講座の課題で、トレロ風のタスク管理アプリを作っている。作業フォルダは `beginner_version/`。完成したらポートフォリオとして公開する
- 講座の目的は、AIと相談しながら「要件定義 → 設計 → 環境構築 → 実装 → テスト → デプロイ」を一通り経験すること。締め切りはない
- 技術スタックは講座の指定で、Java 21 / Spring Boot、React（Vite）、MySQL 8.4、AWS
- 私（ユーザー）はWeb制作の経験はあるが、プログラミングは未経験。コードは基本的にAIに書いてもらう。ただし自分で説明できるように、何をしているかを解説してほしい

## 進め方の希望
- 決めごとは、講義動画と同じく **質問を1つずつ出し、おすすめと選択肢を示す形式** で進めてほしい
- 決まったことは、毎回「ここまでの決定」としてまとめてほしい
- 勉強のため、あえて難しいほうを選ぶことがある

## 完了したこと
1. **要件定義**：確定済み。詳しくは `beginner_version/docs/requirements.md` を必ず読むこと
   - ボードはユーザーごとに1つ
   - リストは追加・名前変更・削除・ドラッグ＆ドロップでの並び替えができる。初期状態は「未着手／作業中／完了」
   - カードの項目はタイトル・説明文・重要度・緊急度・期限・ラベル（複数）（2026-10-25 に「優先度」を「重要度」に改め、「緊急度」を追加）。タイトルだけで追加し、詳細はモーダルで編集する
   - ラベルは自由に追加・編集・削除できる
   - メールアドレスとパスワードでログインする。ゲストログインあり
   - 後回し：複数ボード、パスワード再設定、検索・絞り込み
2. **開発環境の構築**（Windows 11）
   - Node.js v26 / npm、Git、VS Code：インストール済み
   - JDK：Eclipse Temurin 21。JAVA_HOME は設定済み
   - MySQL 8.4.9：Windowsサービス `MySQL84`、**ポートは3307**
   - DB は `task_app`（utf8mb4）、アプリ用ユーザーは `task_app_user`@localhost。パスワードは私だけが知っている。コードやGitには書かず、環境変数で渡すこと
   - **注意**：ポート3306は別プロジェクト（cytech_step9_app）の Docker MySQL が使っている。触らないこと

3. **テーブル設計**：確定済み。詳しくは `beginner_version/docs/database-design.md` を必ず読むこと
   - テーブルは users / boards / board_columns / cards / labels / card_labels
   - 主キーは BIGINT AUTO_INCREMENT、並び順は 0 からの連番、削除は ON DELETE CASCADE
   - ゲストはログインのたびに一時ユーザーを作る（is_guest 列＋ダミーメール）
   - テーブル作成は Flyway で管理する

4. **要件定義書・テーブル設計の見直し（🔴 必須項目）**：完了。要件定義書の U-01〜U-10・3.7、テーブル設計書の6〜8章に反映済み
   - パスワード8〜64文字（半角のみ）、メールは小文字にそろえる、登録後は自動ログイン
   - 認証はセッション方式（CSRF対策あり）、無操作30分でログアウト
   - IPごとの回数制限（ログイン失敗15分10回、ゲスト作成1時間10回）
   - 上限：リスト20／カード100／ラベル30
   - ゲストはログアウトで即削除＋1時間ごとに24時間たったものを削除
   - DBの日時はUTC。所有者は毎回たどって確認し、他人のデータには404を返す

5. **画面設計**：完了。`screen-design.md`（画面一覧・遷移図・項目・メッセージ一覧）と `wireframes.html`（ブラウザで開く）
   - 図は Mermaid（VS Code の拡張機能 Markdown Preview Mermaid Support で表示）、ワイヤーフレームは HTML
   - 雰囲気はシンプルな業務ツール風（白とグレー基調）。アプリ名は仮に「TaskBoard」
   - ボード名はクリックで変更可（要件 B-01）。カード詳細は保存ボタンでまとめて保存
   - リストは0個も可。説明文は改行反映＋http/https のURLをリンク化。期限は年が違うときだけ年を表示
   - ラベルの色は8色（screen-design.md 6章）

6. **API設計**：完了。`api-design.md`（API一覧は3章、エラーは4章）
   - REST形式・浅い入れ子。ボードは `/api/board`（IDなし）
   - `GET /api/board` の1回でボード・リスト・カード・ラベルをまとめて返す。カードはラベルIDだけを持つ
   - 並び替えは、動かしたもの1件と移動先の位置（0から）を送り、振り直しはサーバーが行う
   - エラーは Problem Details（RFC 9457）＋ `code`・`errors`
   - CSRFは Cookie `XSRF-TOKEN` → ヘッダー `X-XSRF-TOKEN`
   - ログインはJSONを受け取るAPIを自作（`AuthenticationManager` を使う）

7. **退会**：要件 U-11 として追加。ヘッダーのメールアドレスを押すとメニュー（ログアウト・退会する）。退会はパスワードで本人確認（画面 W-08、API A-19 `POST /api/auth/withdraw`）。ゲストには出さない。メールアドレス・パスワードの変更は後回し

8. **非機能要件・テスト方針**：要件定義書5章に反映済み
   - AWSは無料枠の範囲だけ（AWS Budgets で通知）。無料枠の条件はアカウント作成時期で違うので、AWS構成のときに確かめる
   - バックアップは1日1回・7日分
   - ログはエラーとセキュリティ関係を記録し、秘密情報・メールアドレスは残さない。14日で削除
   - セキュリティは OWASP Top 10 を一通りカバー（5.2の表）
   - 性能はカード2,000枚のボードで表示1秒以内・その他0.5秒以内、同時10人
   - テストはサーバーは JUnit 中心、React はロジック部分だけ Vitest、画面全体は手動。GitHub Actions で自動実行

9. **ドラッグ＆ドロップのライブラリ**：@hello-pangea/dnd に決定（要件定義書6章）。候補は dnd-kit（core・react）、Pragmatic drag and drop と比べた。ボード専用で書く量が少なく、説明しやすいため

10. **緊急度の追加**：ワイヤーフレームを見て追加した
    - 「優先度」を「重要度」に改め、別に「緊急度」（高・中・低、初期値は中）を追加（要件 C-06・C-08）
    - カードの左端の帯は重要度。緊急度が「高」のときだけ「至急」の札（赤い文字＋赤い枠）を出す
    - DBの列は `importance`・`urgency`、APIの項目名も同じ

11. **モックアップ**：完了（2026-10-25、私が触って「今の形でOK」と確認）。場所は `beginner_version/mockup/`（開き方は `mockup/README.md`）
    - 決定：ボード・カード詳細・ラベル管理・確認ダイアログは本物どおりに動かし、ログイン・登録・退会は画面と入力チェックだけ／localStorage に保存／ドラッグ＆ドロップは SortableJS（CDN）／JS は部品ごとに分けて ES モジュールでつなぐ／確認は `npx serve mockup`
    - `js/api.js` はニセのサーバー。API設計書の A-01〜A-19 と同じ関数・同じ形（Problem Details のエラーも同じ）。React に移すときは fetch に置き換える
    - 左下の「モック操作」パネルで、通信の遅れ・失敗・セッション切れを起こせる
    - 画面のロボット操作（Chrome を画面なしで動かす）で、主な操作41項目が期待どおり動くことを確認済み

12. **モックアップで決まったことを設計書に反映**：完了（2026-10-25）
    - 要件 C-09 を追加：カードに説明文を3行まで表示し、長いときは「もっと見る」でカードの中に全文を開く（「≡ 説明あり」はやめた）
    - `screen-design.md` に細かい動きを追記：名前変更は外をクリックでも確定（空なら元に戻す）、リスト追加後も入力欄を開いたまま、上限はリスト・カードは右下の通知・ラベルは入力欄の下、カード詳細の上にラベル管理を重ねて開く、ログイン失敗でパスワード欄を空にする、ボードの読み込み失敗の表示、入力チェックは送信時、日本語入力の Enter の扱い（5章）
    - `wireframes.html` の W-03 を、カードに説明を表示する形に直した

13. **詳細設計**：完了（2026-10-25）。`docs/detailed-design.md`。AI用のルールは `beginner_version/CLAUDE.md`
    - サーバー：Controller → Service → Repository の3層、パッケージは機能ごと（`io.github.keiwebcreate.taskboard`）、DTO は record、エンティティは子→親の `@ManyToOne` だけ（ボード画面はSQL 3回）、所有者の確認は「ID＋持ち主」で探す Repository のメソッド、エラーは独自の例外＋`@RestControllerAdvice`、セッションは Spring Session JDBC（MySQL）、Gradle、Lombok なし、テストDBは Testcontainers
    - 画面：TypeScript、`useReducer`＋Context、CSS Modules、フォルダは機能ごと（`features/`・`shared/`）、React Router、fetch を包んだ自作の関数
    - ゲストのサンプルデータはモックアップと同じ中身に決定

## 次にやること

1. **Spring Boot と React のひな形を作る**（`backend/`・`frontend/`。詳細設計書 2章・3.1・4.1）。Flyway の V1（テーブル）・V2（セッション）を実際の MySQL（3307）で流して確かめる。Testcontainers には Docker が必要
2. 未決事項：AWSの構成、回数制限の実装方法（`api-design.md` 8章・`detailed-design.md` 7章）

### 見直しで残っている項目（急がない）
- 🟡 要件・テーブル：すべて完了。CREATE TABLE のSQLはまだ実際のMySQLで流していないので、Flyway を作るときに確かめる
- ⚪ 要件：改訂履歴、前提条件と制約、非機能要件のID、受け入れ基準
- ⚪ テーブル：論理名と物理名、CRUD表

## Git
- `beginner_version/` が Git リポジトリ。ブランチは `main`、リモートは `https://github.com/keiwebcreate/raise-tech-beginner-edition.git`
- `beginner_version/CLAUDE.md` に「add・commit・push・ブランチ操作は承認なしで許可」と書いてある
- 2026-10-25 時点で、設計書とモックアップはすべてコミットして GitHub にプッシュ済み
