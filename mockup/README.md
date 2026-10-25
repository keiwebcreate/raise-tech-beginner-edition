# TaskBoard モックアップ

HTML・CSS・JavaScript だけで作った、触って確かめるためのモックアップ。本番のコードではない（本番は React で作り直す）。

## 開き方

`beginner_version` フォルダでターミナルを開き、次を実行する。

```
npx serve mockup
```

表示された URL（例：`http://localhost:3000`）をブラウザで開く。止めるときは `Ctrl + C`。

- ファイルを直接ダブルクリックで開くと動かない（`import` / `export` を使っているため）
- ドラッグ＆ドロップのライブラリ（SortableJS）をインターネットから読み込むので、オフラインではドラッグできない

## 試し方

- ログイン：`taro@example.com` ／ `password123`（ほかに「ゲストとして試す」・新規登録も使える）
- データはブラウザの localStorage に保存される。再読み込みしても残る
- 左下の「モック操作」パネルで、本物のサーバーでしか起きないことをわざと起こせる
  - 通信の待ち時間：ボタンが押せなくなる様子を見る
  - 次の通信を失敗させる：通知や、ドラッグの取り消しを見る
  - セッション切れにする：次の操作でログイン画面に戻る様子を見る
  - データを最初に戻す：すべてサンプルデータに戻す

## ファイルの役割

| ファイル | 役割 | React に移すとき |
|---|---|---|
| `index.html` | 入口 | Vite の `index.html` |
| `style.css` | 見た目（`docs/wireframes.html` の CSS を流用） | そのまま参考にする |
| `js/main.js` | URL と画面の結び付け | React Router の設定 |
| `js/router.js` | 画面の切り替え（`#/login` など） | React Router |
| `js/api.js` | **ニセのサーバー**。API設計書の A-01〜A-19 と同じ関数・同じ形 | `fetch` で `/api/...` を呼ぶ関数に置き換える |
| `js/board-store.js` | 表示中のボードのデータ | `useState` / Context |
| `js/sample-data.js` | サンプルデータ（`GET /api/board` と同じ形） | テストのデータに使える |
| `js/constants.js` | 上限・ラベルの色・メッセージ | そのまま使える |
| `js/validation.js` | 入力チェック | そのまま使える |
| `js/utils.js` | 期限の表示・URLのリンク化など | そのまま使える（Vitest でテストする部分） |
| `js/errors.js` | APIエラーの共通の扱い | そのまま参考にする |
| `js/screens/` | ログイン・登録・ボード・404 の画面 | ページのコンポーネント |
| `js/modals/` | カード詳細・ラベル管理・退会 | モーダルのコンポーネント |
| `js/ui/` | モーダルの土台・確認ダイアログ・通知 | 共通のコンポーネント |
| `js/mock-panel.js` | モック操作パネル | 作らない |
