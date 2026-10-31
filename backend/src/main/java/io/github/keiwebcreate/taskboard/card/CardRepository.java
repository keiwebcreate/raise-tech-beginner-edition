package io.github.keiwebcreate.taskboard.card;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * cards テーブルとのやりとり。
 */
public interface CardRepository extends JpaRepository<Card, Long> {

	/**
	 * ボードの全カードを、付いているラベルと一緒に1回の SQL で取る（詳細設計書 3.8.1）。
	 * left join fetch：ラベルも同時に読み込む。ラベルのないカードも取れる。
	 * これがないと、カード1枚ごとにラベルを読む SQL が走ってしまう（N+1 問題）。
	 */
	@Query("""
			select c from Card c
			left join fetch c.labels
			where c.column.board.id = :boardId
			order by c.position
			""")
	List<Card> findAllForBoard(Long boardId);

	// 自分のカード1件。ID と「持ち主（カード → リスト → ボード → ユーザー）」の両方を条件にして探す（詳細設計書 3.5）
	Optional<Card> findByIdAndColumnBoardUserId(Long cardId, Long userId);

	// SELECT ... FROM cards WHERE column_id = ? ORDER BY position（リストのカードを上から順に。振り直しに使う）
	List<Card> findByColumnIdOrderByPosition(Long columnId);

	// リストのカードの数（上限の確認と、一番下に追加するときの位置に使う）
	long countByColumnId(Long columnId);

}
