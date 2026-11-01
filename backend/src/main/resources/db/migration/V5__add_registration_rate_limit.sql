-- ユーザー登録の回数制限（要件 U-10。テーブル設計書 3.9。イシュー #53）
-- rate_limit_events.kind に入れてよい値に 'REGISTRATION' を足す。
-- CHECK 制約は書き換えられないので、消してから作り直す（今ある行はそのまま）

ALTER TABLE rate_limit_events DROP CONSTRAINT chk_rate_limit_events_kind;
ALTER TABLE rate_limit_events ADD CONSTRAINT chk_rate_limit_events_kind
    CHECK (kind IN ('LOGIN_FAILURE', 'GUEST_CREATION', 'REGISTRATION'));
