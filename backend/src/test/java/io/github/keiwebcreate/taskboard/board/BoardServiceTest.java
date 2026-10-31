package io.github.keiwebcreate.taskboard.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import io.github.keiwebcreate.taskboard.TestcontainersConfiguration;
import io.github.keiwebcreate.taskboard.board.dto.BoardResponse;
import io.github.keiwebcreate.taskboard.board.dto.ColumnWithCardsResponse;
import io.github.keiwebcreate.taskboard.card.Level;
import io.github.keiwebcreate.taskboard.card.dto.CardResponse;
import io.github.keiwebcreate.taskboard.common.error.NotFoundException;
import io.github.keiwebcreate.taskboard.label.dto.LabelResponse;
import io.github.keiwebcreate.taskboard.user.UserRepository;

/**
 * BoardService のボード取得（A-06）のテスト。
 * - @DataJpaTest に BoardService を足して起動する（Web の部品は起動しない）
 * - @Sql：テストの前に board-test-data.sql でテストデータを DB に入れる。テストが終わると元に戻る
 */
@DataJpaTest
@Import({ TestcontainersConfiguration.class, BoardService.class })
@Sql("/board/board-test-data.sql")
class BoardServiceTest {

	@Autowired
	private BoardService boardService;

	@Autowired
	private UserRepository userRepository;

	@Test
	void ボードとリストとカードとラベルをまとめて取得できる() {
		BoardResponse board = boardService.getBoard(userIdOf("taro@example.com"));

		assertThat(board.name()).isEqualTo("たろうのボード");

		// ラベルは作成順
		assertThat(board.labels()).extracting(LabelResponse::name).containsExactly("仕事", "買い物");
		assertThat(board.labels().get(0).color()).isEqualTo("#e5484d");

		// リストは左から順。カードのないリストも含む
		assertThat(board.columns()).extracting(ColumnWithCardsResponse::name)
				.containsExactly("未着手", "作業中", "完了");
		assertThat(board.columns().get(1).cards()).isEmpty();

		// カードは上から順
		List<CardResponse> todoCards = board.columns().get(0).cards();
		assertThat(todoCards).extracting(CardResponse::title).containsExactly("メールを返す", "牛乳を買う");
		assertThat(board.columns().get(2).cards()).extracting(CardResponse::title).containsExactly("部屋の掃除");
	}

	@Test
	void カードの項目と付いているラベルのIDが入る() {
		BoardResponse board = boardService.getBoard(userIdOf("taro@example.com"));
		Long workLabelId = board.labels().get(0).id(); // 仕事
		Long shoppingLabelId = board.labels().get(1).id(); // 買い物

		List<CardResponse> todoCards = board.columns().get(0).cards();

		CardResponse mail = todoCards.get(0);
		assertThat(mail.description()).isEqualTo("取引先に返信する");
		assertThat(mail.importance()).isEqualTo(Level.HIGH);
		assertThat(mail.urgency()).isEqualTo(Level.LOW);
		assertThat(mail.dueDate()).isNull();
		assertThat(mail.labelIds()).containsExactly(workLabelId, shoppingLabelId);

		CardResponse milk = todoCards.get(1);
		assertThat(milk.description()).isNull();
		assertThat(milk.importance()).isEqualTo(Level.MEDIUM);
		assertThat(milk.urgency()).isEqualTo(Level.HIGH);
		assertThat(milk.dueDate()).isEqualTo(LocalDate.of(2026, 10, 30));
		assertThat(milk.labelIds()).containsExactly(shoppingLabelId);

		// ラベルのないカードは空のリスト
		assertThat(board.columns().get(2).cards().get(0).labelIds()).isEmpty();
	}

	@Test
	void 他人のデータは混ざらない() {
		BoardResponse taro = boardService.getBoard(userIdOf("taro@example.com"));
		assertThat(taro.labels()).extracting(LabelResponse::name).doesNotContain("趣味");
		assertThat(taro.columns()).extracting(ColumnWithCardsResponse::name).doesNotContain("はなこのリスト");

		BoardResponse hanako = boardService.getBoard(userIdOf("hanako@example.com"));
		assertThat(hanako.name()).isEqualTo("はなこのボード");
		assertThat(hanako.labels()).extracting(LabelResponse::name).containsExactly("趣味");
		assertThat(hanako.columns()).extracting(ColumnWithCardsResponse::name).containsExactly("はなこのリスト");
		assertThat(hanako.columns().get(0).cards()).extracting(CardResponse::title).containsExactly("はなこのカード");
	}

	@Test
	void ボードがないユーザーではNotFoundExceptionになる() {
		Long jiroId = userIdOf("jiro@example.com");

		assertThatThrownBy(() -> boardService.getBoard(jiroId)).isInstanceOf(NotFoundException.class);
	}

	private Long userIdOf(String email) {
		return userRepository.findByEmail(email).orElseThrow().getId();
	}

}
