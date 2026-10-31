-- BoardServiceTest のテストデータ
-- ID は DB が自動で振るので、ほかの行は「メールアドレス」や「名前」で探してつなぐ
-- 並び順のテストのため、わざと position の順番とは違う順番で入れている

-- ユーザー：たろう（テストの主役）、はなこ（他人）、じろう（ボードなし）
INSERT INTO users (email, password_hash) VALUES
    ('taro@example.com',   'dummy'),
    ('hanako@example.com', 'dummy'),
    ('jiro@example.com',   'dummy');

-- ボード
INSERT INTO boards (user_id, name)
SELECT id, 'たろうのボード' FROM users WHERE email = 'taro@example.com';
INSERT INTO boards (user_id, name)
SELECT id, 'はなこのボード' FROM users WHERE email = 'hanako@example.com';

-- ラベル（作成順：仕事 → 買い物）
INSERT INTO labels (user_id, name, color)
SELECT id, '仕事', '#e5484d' FROM users WHERE email = 'taro@example.com';
INSERT INTO labels (user_id, name, color)
SELECT id, '買い物', '#30a46c' FROM users WHERE email = 'taro@example.com';
INSERT INTO labels (user_id, name, color)
SELECT id, '趣味', '#0090ff' FROM users WHERE email = 'hanako@example.com';

-- リスト（入れる順：完了 → 未着手 → 作業中。左からの並びは 未着手 → 作業中 → 完了）
INSERT INTO board_columns (board_id, name, position)
SELECT b.id, '完了', 2 FROM boards b JOIN users u ON u.id = b.user_id WHERE u.email = 'taro@example.com';
INSERT INTO board_columns (board_id, name, position)
SELECT b.id, '未着手', 0 FROM boards b JOIN users u ON u.id = b.user_id WHERE u.email = 'taro@example.com';
INSERT INTO board_columns (board_id, name, position)
SELECT b.id, '作業中', 1 FROM boards b JOIN users u ON u.id = b.user_id WHERE u.email = 'taro@example.com';
INSERT INTO board_columns (board_id, name, position)
SELECT b.id, 'はなこのリスト', 0 FROM boards b JOIN users u ON u.id = b.user_id WHERE u.email = 'hanako@example.com';

-- カード（「未着手」の中は、入れる順：牛乳を買う → メールを返す。上からの並びは メールを返す → 牛乳を買う）
INSERT INTO cards (column_id, title, description, importance, urgency, due_date, position)
SELECT id, '牛乳を買う', NULL, 'MEDIUM', 'HIGH', DATE '2026-10-30', 1 FROM board_columns WHERE name = '未着手';
INSERT INTO cards (column_id, title, description, importance, urgency, due_date, position)
SELECT id, 'メールを返す', '取引先に返信する', 'HIGH', 'LOW', NULL, 0 FROM board_columns WHERE name = '未着手';
INSERT INTO cards (column_id, title, position)
SELECT id, '部屋の掃除', 0 FROM board_columns WHERE name = '完了';
INSERT INTO cards (column_id, title, position)
SELECT id, 'はなこのカード', 0 FROM board_columns WHERE name = 'はなこのリスト';

-- カードに付けるラベル（メールを返す：仕事・買い物、牛乳を買う：買い物、はなこのカード：趣味）
INSERT INTO card_labels (card_id, label_id)
SELECT c.id, l.id FROM cards c, labels l WHERE c.title = 'メールを返す' AND l.name IN ('仕事', '買い物');
INSERT INTO card_labels (card_id, label_id)
SELECT c.id, l.id FROM cards c, labels l WHERE c.title = '牛乳を買う' AND l.name = '買い物';
INSERT INTO card_labels (card_id, label_id)
SELECT c.id, l.id FROM cards c, labels l WHERE c.title = 'はなこのカード' AND l.name = '趣味';
