-- 複数ボード（要件 B-02〜B-04。イシュー #51。テーブル設計書 3.2）
-- 1人1ボードの一意制約を外し、1人が複数のボードを持てるようにする。今あるボードはそのまま残る。
-- 一意制約がなくなると user_id のインデックスもなくなるので、「自分のボードの一覧」を探すためのインデックスを作り直す

ALTER TABLE boards DROP CONSTRAINT uk_boards_user;
CREATE INDEX idx_boards_user ON boards (user_id);
