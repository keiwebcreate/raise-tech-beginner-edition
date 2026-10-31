package io.github.keiwebcreate.taskboard.column;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * board_columns テーブルとのやりとり。
 */
public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {

	// SELECT ... FROM board_columns WHERE board_id = ? ORDER BY position（ボードのリスト一覧を左から順に）
	List<BoardColumn> findByBoardIdOrderByPosition(Long boardId);

}
