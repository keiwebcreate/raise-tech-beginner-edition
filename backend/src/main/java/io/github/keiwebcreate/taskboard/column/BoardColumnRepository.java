package io.github.keiwebcreate.taskboard.column;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * board_columns テーブルとのやりとり。
 */
public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {

	// SELECT ... FROM board_columns WHERE board_id = ? ORDER BY position（ボードのリスト一覧を左から順に）
	List<BoardColumn> findByBoardIdOrderByPosition(Long boardId);

	// 自分のリスト1件。ID と「持ち主（ボードのユーザー）」の両方を条件にして探す（詳細設計書 3.5）
	// 他人のリストの ID を指定されても見つからない
	Optional<BoardColumn> findByIdAndBoardUserId(Long columnId, Long userId);

	// ボードのリストの数（上限の確認と、右端に追加するときの位置に使う）
	long countByBoardId(Long boardId);

}
