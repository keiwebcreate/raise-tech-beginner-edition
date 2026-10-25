-- テーブル作成（テーブル設計書 database-design.md 10章の SQL と同じ）

-- 更新日時を自動で入れる関数（テーブル設計書 3.8）
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
