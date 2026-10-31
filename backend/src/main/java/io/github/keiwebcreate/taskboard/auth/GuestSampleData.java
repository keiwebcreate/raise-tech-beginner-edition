package io.github.keiwebcreate.taskboard.auth;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.github.keiwebcreate.taskboard.board.Board;
import io.github.keiwebcreate.taskboard.board.BoardRepository;
import io.github.keiwebcreate.taskboard.card.Card;
import io.github.keiwebcreate.taskboard.card.CardRepository;
import io.github.keiwebcreate.taskboard.card.Level;
import io.github.keiwebcreate.taskboard.column.BoardColumn;
import io.github.keiwebcreate.taskboard.column.BoardColumnRepository;
import io.github.keiwebcreate.taskboard.label.Label;
import io.github.keiwebcreate.taskboard.label.LabelRepository;
import io.github.keiwebcreate.taskboard.user.User;

/**
 * ゲストのボードに入れるサンプルデータ（要件 U-06、テーブル設計書 9.2）。使い方の説明をかねる。
 * 中身はモックアップの createGuestSampleBoard（mockup/js/sample-data.js）と同じ。
 * AuthService.guest のトランザクションの中で呼ぶ。
 */
@Component
public class GuestSampleData {

	// 期限の「今日」は日本の日付で数える。サーバーは UTC で動くので、そのままだと日本の朝9時より前は前の日になる
	static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

	private final BoardRepository boardRepository;
	private final BoardColumnRepository boardColumnRepository;
	private final CardRepository cardRepository;
	private final LabelRepository labelRepository;

	public GuestSampleData(BoardRepository boardRepository, BoardColumnRepository boardColumnRepository,
			CardRepository cardRepository, LabelRepository labelRepository) {
		this.boardRepository = boardRepository;
		this.boardColumnRepository = boardColumnRepository;
		this.cardRepository = cardRepository;
		this.labelRepository = labelRepository;
	}

	public void create(User user) {
		Label sample = labelRepository.save(new Label(user, "サンプル", "#f76b15"));
		Label work = labelRepository.save(new Label(user, "仕事", "#3e63dd"));
		Label privateLabel = labelRepository.save(new Label(user, "プライベート", "#30a46c"));

		Board board = boardRepository.save(new Board(user, "マイボード"));
		BoardColumn todo = boardColumnRepository.save(new BoardColumn(board, "未着手", 0));
		BoardColumn doing = boardColumnRepository.save(new BoardColumn(board, "作業中", 1));
		boardColumnRepository.save(new BoardColumn(board, "完了", 2));

		LocalDate today = LocalDate.now(JAPAN);
		addCard(todo, 0, "サンプル：右のリストへドラッグしてみよう", null, Level.MEDIUM, Level.MEDIUM, null, sample);
		addCard(todo, 1, "会議の資料を準備する", null, Level.HIGH, Level.HIGH, today, work);
		addCard(todo, 2, "旅行の計画を立てる", null, Level.LOW, Level.MEDIUM, today.plusDays(20), privateLabel);
		addCard(doing, 0, "カードをクリックして詳細を編集",
				"タイトル・説明・重要度・緊急度・期限・ラベルを編集できます。\n詳しくは https://example.com",
				Level.HIGH, Level.MEDIUM, null, sample);
	}

	private void addCard(BoardColumn column, int position, String title, String description, Level importance,
			Level urgency, LocalDate dueDate, Label label) {
		Card card = cardRepository.save(new Card(column, title, position));
		// 重要度などは、カード詳細の保存（A-13）と同じメソッドで入れる。save のあとの変更も、トランザクションの終わりに DB へ書き込まれる
		card.update(title, description, importance, urgency, dueDate, Set.of(label));
	}

}
