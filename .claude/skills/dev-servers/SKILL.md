---
name: dev-servers
description: TaskBoard の開発サーバー（DB・Spring Boot・Vite）を、決まったポート（5432・8080・5173）で起動・停止する手順。動作確認でサーバーを起動するとき、ポートが使われていて起動できないとき、サーバーを止めるときに必ず使う。
---

# 開発サーバーの起動と停止

動作確認のためにサーバーを起動するときは、この手順で行う。コマンドは PowerShell で、`beginner_version` フォルダを基準にする。

## 守ること

- ポートは必ず次のとおり。**別のポートでは起動しない**（`--port`・`server.port`・`compose.yaml` のポートの書き換え、一時的な変更も含む）
  - 別のポートで動かすと、Vite の `/api` の転送先（8080）や Spring Boot の DB の接続先（5432）とずれて、うまく動かないため

| ポート | 使うもの | 起動 |
|---|---|---|
| 5432 | PostgreSQL 18（Docker のコンテナ `taskboard-db-1`） | `docker compose up -d` |
| 8080 | Spring Boot（API） | `backend` で `.\gradlew.bat bootRun` |
| 5173 | Vite（画面） | `frontend` で `npm run dev` |

- ポートが使われていたら、**使っているものを止めてから**決まったポートで起動する（ユーザーの決定。何が使っていても止めてよい）
- 3306（別のプロジェクトの MySQL）は、このアプリのポートではないので触らない

## 1. ポートが空いているか確かめる

```powershell
foreach ($port in 5432, 8080, 5173) {
  $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($conn) { "$port : 使用中 → $((Get-Process -Id $conn.OwningProcess).ProcessName) (PID $($conn.OwningProcess))" } else { "$port : 空き" }
}
```

## 2. 使っているものを止める

使っているものの種類で止め方が違う。止めたら、もう一度 1 で空いたことを確かめる。

| 使っているもの | 止め方 |
|---|---|
| Docker のコンテナ（プロセス名が `com.docker.backend`・`wslrelay` など） | Docker のプロセス自体は止めない（Docker 全体が止まる）。`docker ps --filter "publish=5432"` でコンテナを探し、`docker stop <コンテナ名>` |
| このアプリの古い Spring Boot・Vite（`java`・`node`） | Claude が背景で起動したものなら、そのタスクを止める。そうでなければ `Stop-Process -Id <PID>` |
| Windows のサービス（例：`postgresql-x64-18` の `postgres`） | `Stop-Service postgresql-x64-18`。管理者の権限が要って止められないときは、ユーザーに「サービス」アプリから止めてもらう |
| そのほかのプロセス | 何のプロセスかをユーザーに伝えてから `Stop-Process -Id <PID>` |

## 3. 起動する（DB → Spring Boot → Vite の順）

1. **DB**：`beginner_version` で `docker compose up -d`。`docker exec taskboard-db-1 pg_isready -U task_app_user -d task_app` が `accepting connections` になるまで待つ
   - `DB_PASSWORD を設定してください` と出たら、環境変数が読めていない。VS Code を開き直してもらう
2. **Spring Boot**：`backend` で `.\gradlew.bat bootRun` を**背景で**（`run_in_background`）起動する。ログに `Started TaskBoardApplication` が出て、8080 が使用中になるまで待つ
3. **Vite**：`frontend` で `npm run dev` を**背景で**起動する。ログに `http://localhost:5173/` が出るまで待つ
   - `Port 5173 is already in use` で止まったら、2 に戻って 5173 を空ける（`strictPort` の設定で、別のポートには逃げない）

## 4. 動いたか確かめる

- 画面：`Invoke-WebRequest http://localhost:5173 -UseBasicParsing` が 200
- API：`Invoke-WebRequest http://localhost:5173/api/board -UseBasicParsing -SkipHttpErrorCheck` で、Vite 経由で Spring Boot に届く（ログインがまだないので、今は 401 が返れば届いている）
- ユーザーには `http://localhost:5173` を開いてもらう

## 5. 止める

- Vite・Spring Boot：背景のタスクを止める（またはターミナルで Ctrl＋C）。残っていたら 1 で PID を調べて `Stop-Process`
- DB：`beginner_version` で `docker compose stop`（データはボリュームに残る）
