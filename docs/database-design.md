# テーブル設計書：トレロ風タスク管理アプリ

最終更新：2026-10-25（DBを MySQL から PostgreSQL 18 に変更）

要件は `requirements.md` を参照。

## 1. 方針

| 項目 | 決定 | 理由 |
|---|---|---|
| DB | PostgreSQL 18 | 講座の指定 |
| ボード | `boards` テーブルを独立させる（今は1ユーザー1ボード） | 後回しにした「複数ボード」をテーブルの作り直しなしで追加できる |
| リストのテーブル名 | `board_columns`（Javaクラスは `BoardColumn`） | `columns` は SQL で「テーブルの列」の意味でも使われ、紛らわしいため |
| 主キー | `BIGINT GENERATED ALWAYS AS IDENTITY`（自動採番） | 一般的で扱いやすい。IDを推測されても、所有者チェック（要件 U-05）で守る |
| 並び順 | `position` 列に 0 から始まる連番 | 仕組みがわかりやすい。移動したら同じリスト内を振り直す（4章） |
| 削除 | 外部キーに `ON DELETE CASCADE` を付け、DBに任せる | 消し忘れが起きない。ゲストデータも users の1行を消すだけで済む |
| 重要度・緊急度 | 列 `importance`・`urgency`。どちらも文字列 `HIGH` / `MEDIUM` / `LOW`（Javaは共通の enum） | DBを見ても意味がわかる |
| ラベルの色 | 色コード（`#e5484d` 形式） | DBを見て色がわかる。選択肢の一覧は Java と React の両方に持ち、サーバーでも一覧にない色を受け付けない |
| ラベルの持ち主 | ユーザー単位（`labels.user_id`）。ボード単位にはしない | 今は1人1ボードなので、どちらでも同じ動きになる。ユーザー単位なら、所有者の確認が `labels.user_id` を見るだけで済む。複数ボードを追加したときも、同じラベルを全ボードで共通に使える。ボードごとに分けたくなったら、`board_id` 列を追加する |
| ラベル名 | ユーザーごとに重複不可 | カード詳細で同じ名前が並ばないようにする |
| CHECK制約 | 基本的なものだけ付ける（3.7） | Java のチェックをすり抜けた値を、DBが最後の砦として止める |
| ゲスト | ゲストログインのたびに一時ユーザーを作る | 通常ユーザーと同じ仕組みで動かせる（5章） |
| テーブル作成 | Flyway で管理する | SQLファイルで変更履歴が残り、ローカルとAWSで同じテーブルを作れる |
| 文字コード | UTF8（DBを作るときの標準。日本語・絵文字を保存できる） | 文字列の比べ方は PostgreSQL の標準のまま。PostgreSQL は文字を厳密に比べるので、濁点の有無や大文字・小文字は別の文字として扱われる（3.5） |
| 日時 | `TIMESTAMPTZ`（タイムゾーン付き）。中身は常にUTCで保存される | サーバーやPCのタイムゾーン設定が違っても、同じ時刻として正しく比べられる（6章） |
| 更新日時 | DBのトリガーで自動更新する（3.8） | PostgreSQL には MySQL の `ON UPDATE CURRENT_TIMESTAMP` がないため。手でSQLを流して更新したときも必ず更新される |
| 所有者の確認 | 毎回、持ち主をたどって確認する（テーブルに user_id を重複して持たせない） | 同じ情報を何か所にも持つと、食い違いが起きるため（7章） |

## 2. ER図

```mermaid
erDiagram
    users ||--|| boards : "持つ"
    users ||--o{ labels : "持つ"
    boards ||--o{ board_columns : "持つ"
    board_columns ||--o{ cards : "持つ"
    cards ||--o{ card_labels : ""
    labels ||--o{ card_labels : ""

    users {
        BIGINT id PK
        VARCHAR email UK
        VARCHAR password_hash
        BOOLEAN is_guest
    }
    boards {
        BIGINT id PK
        BIGINT user_id FK,UK
        VARCHAR name
    }
    board_columns {
        BIGINT id PK
        BIGINT board_id FK
        VARCHAR name
        INTEGER position
    }
    cards {
        BIGINT id PK
        BIGINT column_id FK
        VARCHAR title
        VARCHAR description
        VARCHAR importance
        VARCHAR urgency
        DATE due_date
        INTEGER position
    }
    labels {
        BIGINT id PK
        BIGINT user_id FK
        VARCHAR name
        CHAR color
    }
    card_labels {
        BIGINT card_id PK,FK
        BIGINT label_id PK,FK
    }
```

## 3. テーブル定義

全テーブル共通で、次の列を持つ（`card_labels` は `created_at` のみ）。

| 列 | 型 | 内容 |
|---|---|---|
| created_at | TIMESTAMPTZ | 作成日時。DBが自動で入れる |
| updated_at | TIMESTAMPTZ | 更新日時。DBのトリガーが自動で更新する（3.8） |

### 3.1 users（ユーザー）

| 列 | 型 | NULL | 初期値 | 内容 |
|---|---|---|---|---|
| id | BIGINT | 不可 | 自動採番 | 主キー |
| email | VARCHAR(255) | 不可 | | 一意。254文字まで。小文字にそろえて保存する。ゲストは `guest-ランダム文字列@guest.local` |
| password_hash | VARCHAR(255) | 不可 | | BCryptでハッシュ化したパスワード。ゲストはランダムな値のハッシュ |
| is_guest | BOOLEAN | 不可 | false | ゲストユーザーかどうか |

インデックス：`(is_guest, created_at)`（古いゲストを探して削除するため）

### 3.2 boards（ボード）

| 列 | 型 | NULL | 初期値 | 内容 |
|---|---|---|---|---|
| id | BIGINT | 不可 | 自動採番 | 主キー |
| user_id | BIGINT | 不可 | | `users.id` への外部キー。今は一意（1人1ボード）。複数ボードに対応するときに一意制約を外す |
| name | VARCHAR(50) | 不可 | マイボード | ボード名。1〜50文字。画面から変更できる（要件 B-01） |

### 3.3 board_columns（リスト）

| 列 | 型 | NULL | 初期値 | 内容 |
|---|---|---|---|---|
| id | BIGINT | 不可 | 自動採番 | 主キー |
| board_id | BIGINT | 不可 | | `boards.id` への外部キー |
| name | VARCHAR(50) | 不可 | | リスト名。1〜50文字 |
| position | INTEGER | 不可 | | ボード内の並び順。0から始まる連番 |

インデックス：`(board_id, position)`

### 3.4 cards（カード）

| 列 | 型 | NULL | 初期値 | 内容 |
|---|---|---|---|---|
| id | BIGINT | 不可 | 自動採番 | 主キー |
| column_id | BIGINT | 不可 | | `board_columns.id` への外部キー |
| title | VARCHAR(100) | 不可 | | タイトル。1〜100文字 |
| description | VARCHAR(2000) | 可 | NULL | 説明文。2000文字まで |
| importance | VARCHAR(10) | 不可 | MEDIUM | 重要度。`HIGH` / `MEDIUM` / `LOW` |
| urgency | VARCHAR(10) | 不可 | MEDIUM | 緊急度。`HIGH` / `MEDIUM` / `LOW` |
| due_date | DATE | 可 | NULL | 期限（時刻なし） |
| position | INTEGER | 不可 | | リスト内の並び順。0から始まる連番 |

インデックス：`(column_id, position)`

### 3.5 labels（ラベル）

| 列 | 型 | NULL | 初期値 | 内容 |
|---|---|---|---|---|
| id | BIGINT | 不可 | 自動採番 | 主キー |
| user_id | BIGINT | 不可 | | `users.id` への外部キー |
| name | VARCHAR(20) | 不可 | | ラベル名。1〜20文字 |
| color | CHAR(7) | 不可 | | 色コード（例：`#e5484d`）。`screen-design.md` 6章の8色のみ |

一意制約：`(user_id, name)`

PostgreSQL は文字列を厳密に比べるので、「パン」と「ハン」、「Work」と「work」は別の名前として扱われる（どちらも登録できる）。

### 3.6 card_labels（カードとラベルの中間テーブル）

| 列 | 型 | NULL | 内容 |
|---|---|---|---|
| card_id | BIGINT | 不可 | `cards.id` への外部キー |
| label_id | BIGINT | 不可 | `labels.id` への外部キー |

主キー：`(card_id, label_id)`（同じカードに同じラベルを二重に付けられない）
インデックス：`(label_id)`（ラベルからカードを探すため）

**注意**：PostgreSQL は外部キーの列に、インデックスを自動では作らない。この設計では、外部キーの列はすべて上のインデックス・一意制約・主キーの先頭に入っているので、追加は不要。

### 3.7 CHECK制約

| テーブル | ルール |
|---|---|
| boards | `name` が空（空白だけ）でない |
| board_columns | `name` が空でない。`position` が0以上 |
| cards | `title` が空でない。`position` が0以上。`importance`・`urgency` が `HIGH`・`MEDIUM`・`LOW` のどれか |
| labels | `name` が空でない。`color` が `#` と小文字の英数字6桁（例：`#e5484d`） |

- 文字数の上限は列の型（`VARCHAR(50)` など）で決まるので、CHECK制約には書かない
- 色は「色コードの形」だけを確かめ、「8色のどれか」は Java で確かめる。8色をDBで決めると、色を変えるたびにテーブルの変更が必要になるため
- 色の確認には正規表現の演算子 `~` を使う（`color ~ '^#[0-9a-f]{6}$'`）。`~` は大文字・小文字を区別するので、`#E5484D` は通らない
- CHECK制約に引っかかるのは Java のチェック漏れ（バグ）なので、そのときは500エラーになる

### 3.8 更新日時のトリガー

PostgreSQL には「行を更新したら、自動で更新日時を入れる」列の設定がない。そこで、次の2つを作る。

1. 関数 `set_updated_at()`：更新される行の `updated_at` を今の時刻にする（1回だけ作る）
2. トリガー：`card_labels` 以外の各テーブルに「行を更新する直前に `set_updated_at()` を呼ぶ」仕掛けを付ける

SQLは10章。Java（JPA）からは `created_at`・`updated_at` に書き込まない。

## 4. 並び順（position）のルール

- リストはボード内で、カードはリスト内で、0, 1, 2 … と隙間なく番号を振る
- 追加：今ある最大の position ＋ 1（一番右・一番下に並ぶ。要件 L-02, C-01）
- 削除：残ったものを 0 から振り直す
- 移動：移動先のリスト（別のリストへ移動した場合は移動元も）を 0 から振り直す
- 振り直しは1つのトランザクションの中で行い、途中で失敗したら全部元に戻す
- `(column_id, position)` などに一意制約は付けない。振り直しの途中で一時的に番号が重なり、エラーになるため

例：カード「A=0, B=1, C=2, D=3」の D を A と B の間に移動 → A=0, D=1, B=2, C=3

## 5. 削除の連鎖（ON DELETE CASCADE）

| 削除するもの | 一緒に消えるもの |
|---|---|
| users | boards、labels（→ その先もすべて） |
| boards | board_columns → cards → card_labels |
| board_columns | cards → card_labels（要件 L-04） |
| cards | card_labels |
| labels | card_labels（カードからラベルが外れる。カードは消えない。要件 T-03） |

ゲストユーザーは、users の行を削除するだけで関連データがすべて消える（要件 U-08）。

- ゲストがログアウトしたとき：そのゲストの users の行をすぐに削除する
- 定期削除：Spring の `@Scheduled` で1時間ごとに、次のSQLにあたる削除を行う
  ```sql
  DELETE FROM users WHERE is_guest = TRUE AND created_at < now() - INTERVAL '24 hours';
  ```
  `(is_guest, created_at)` のインデックスで、対象をすばやく探せる

## 6. 日時の扱い

- `created_at`・`updated_at` は `TIMESTAMPTZ`。PostgreSQL が内部でUTCに変換して保存するので、接続する側のタイムゾーン設定に左右されない。Java では `Instant`（世界共通のある瞬間）で受け取る
- 念のため、Spring Boot は JVM のタイムゾーンを UTC にして動かす（ログの時刻などをそろえるため）
- `due_date`（期限）は時刻を持たない日付（`DATE`）なので、タイムゾーンの影響を受けない。Java では `LocalDate`
- 期限の色分け（要件 C-05）の「今日」は、React で利用者のブラウザの日付を使って判定する

## 7. 所有者の確認（要件 U-05）

他人のデータを操作させないために、APIで受け取ったIDについて毎回、ログイン中のユーザーのものかを確認する。

| 操作の対象 | 確認のしかた |
|---|---|
| リスト | board_columns → boards とたどり、`boards.user_id` がログイン中のユーザーか |
| カード | cards → board_columns → boards とたどり、`boards.user_id` がログイン中のユーザーか |
| ラベル | `labels.user_id` がログイン中のユーザーか |
| カードへのラベル付け | **カードとラベルの両方** が、ログイン中のユーザーのものか |
| カードの移動 | カードと移動先のリストの両方が、ログイン中のユーザーのものか |

- 他人のデータ（または存在しないデータ）を指定されたら、「404 見つかりません」を返す。403（権限なし）を返すと、そのIDのデータが存在することを教えてしまうため
- card_labels の外部キーは「カードとラベルが存在するか」しか確認しない。他人のラベルを自分のカードに付けることは DB では防げないので、必ずアプリで確認する

## 8. データ量の上限

要件 3.7 のとおり、リストは1ボードに20個、カードは1リストに100枚、ラベルは1ユーザーに30個まで。追加の前にアプリで件数を数えて確認する（DBの制約では表現しない）。

この上限により、1ユーザーのカードは最大2,000枚、並び順の振り直しは最大100行になる。連番方式（4章）で性能の問題は起きない。

## 9. 初期データ

### 9.1 ユーザー登録時（要件 U-02）

- boards に1行（名前「マイボード」）
- board_columns に3行：未着手（0）、作業中（1）、完了（2）

### 9.2 ゲストログイン時（要件 U-06）

- users に1行（`is_guest = true`、ダミーのメールアドレス）
- 9.1 と同じボードとリストに加えて、サンプルのカードとラベル。中身はモックアップ（`mockup/js/sample-data.js` の `createGuestSampleBoard`）と同じにする（`detailed-design.md` 1章）

## 10. テーブル作成SQL（Flyway の `V1__create_tables.sql` の原案）

```sql
-- 更新日時を自動で入れる関数（3.8）
CREATE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE users (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_guest      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);
CREATE INDEX idx_users_guest_created ON users (is_guest, created_at);

CREATE TABLE boards (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY,
    user_id    BIGINT      NOT NULL,
    name       VARCHAR(50) NOT NULL DEFAULT 'マイボード',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_boards PRIMARY KEY (id),
    CONSTRAINT uk_boards_user UNIQUE (user_id),
    CONSTRAINT fk_boards_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_boards_name CHECK (char_length(btrim(name)) > 0)
);

CREATE TABLE board_columns (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY,
    board_id   BIGINT      NOT NULL,
    name       VARCHAR(50) NOT NULL,
    position   INTEGER     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_board_columns PRIMARY KEY (id),
    CONSTRAINT fk_board_columns_board FOREIGN KEY (board_id) REFERENCES boards (id) ON DELETE CASCADE,
    CONSTRAINT chk_board_columns_name CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT chk_board_columns_position CHECK (position >= 0)
);
CREATE INDEX idx_board_columns_board_position ON board_columns (board_id, position);

CREATE TABLE cards (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY,
    column_id   BIGINT        NOT NULL,
    title       VARCHAR(100)  NOT NULL,
    description VARCHAR(2000) NULL,
    importance  VARCHAR(10)   NOT NULL DEFAULT 'MEDIUM',
    urgency     VARCHAR(10)   NOT NULL DEFAULT 'MEDIUM',
    due_date    DATE          NULL,
    position    INTEGER       NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_cards PRIMARY KEY (id),
    CONSTRAINT fk_cards_column FOREIGN KEY (column_id) REFERENCES board_columns (id) ON DELETE CASCADE,
    CONSTRAINT chk_cards_title CHECK (char_length(btrim(title)) > 0),
    CONSTRAINT chk_cards_position CHECK (position >= 0),
    CONSTRAINT chk_cards_importance CHECK (importance IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_cards_urgency CHECK (urgency IN ('HIGH', 'MEDIUM', 'LOW'))
);
CREATE INDEX idx_cards_column_position ON cards (column_id, position);

CREATE TABLE labels (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY,
    user_id    BIGINT      NOT NULL,
    name       VARCHAR(20) NOT NULL,
    color      CHAR(7)     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_labels PRIMARY KEY (id),
    CONSTRAINT uk_labels_user_name UNIQUE (user_id, name),
    CONSTRAINT fk_labels_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_labels_name CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT chk_labels_color CHECK (color ~ '^#[0-9a-f]{6}$')
);

CREATE TABLE card_labels (
    card_id    BIGINT      NOT NULL,
    label_id   BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_card_labels PRIMARY KEY (card_id, label_id),
    CONSTRAINT fk_card_labels_card  FOREIGN KEY (card_id)  REFERENCES cards (id)  ON DELETE CASCADE,
    CONSTRAINT fk_card_labels_label FOREIGN KEY (label_id) REFERENCES labels (id) ON DELETE CASCADE
);
CREATE INDEX idx_card_labels_label ON card_labels (label_id);

-- 更新日時のトリガー（card_labels は更新しないので付けない）
CREATE TRIGGER trg_users_updated_at         BEFORE UPDATE ON users         FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_boards_updated_at        BEFORE UPDATE ON boards        FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_board_columns_updated_at BEFORE UPDATE ON board_columns FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_cards_updated_at         BEFORE UPDATE ON cards         FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_labels_updated_at        BEFORE UPDATE ON labels        FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

セッション（ログイン状態）を保存する Spring Session JDBC のテーブルは、`V2__create_session_tables.sql` で作る（`detailed-design.md` 3.9）。

## 11. まだ決めていないこと

- ~~ゲストのサンプルデータの中身~~ → モックアップと同じ（2026-10-25 決定）
