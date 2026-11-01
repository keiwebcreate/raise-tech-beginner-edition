-- 回数制限の記録（要件 U-10。テーブル設計書 3.9）
-- ログインの失敗とゲストの作成のたびに1行を入れ、「直近の決まった時間に、同じ IP アドレスから何行あるか」を数える。
-- 古い行は RateLimitCleanupJob が毎時消す。ユーザーとは結びつけない（ログイン前の操作なので、まだ誰かわからない）

CREATE TABLE rate_limit_events (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY,
    kind       VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_rate_limit_events PRIMARY KEY (id),
    CONSTRAINT chk_rate_limit_events_kind CHECK (kind IN ('LOGIN_FAILURE', 'GUEST_CREATION'))
);
-- 数えるときの条件（種類・IP アドレス・時刻）の順に並べる
CREATE INDEX idx_rate_limit_events_kind_ip_created ON rate_limit_events (kind, ip_address, created_at);
-- 古い行を消すとき（created_at だけで探す）
CREATE INDEX idx_rate_limit_events_created ON rate_limit_events (created_at);
