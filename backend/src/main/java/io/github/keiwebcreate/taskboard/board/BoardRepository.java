package io.github.keiwebcreate.taskboard.board;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * boards テーブルとのやりとり。
 */
public interface BoardRepository extends JpaRepository<Board, Long> {

	// SELECT ... FROM boards WHERE user_id = ?（自分のボード）
	Optional<Board> findByUserId(Long userId);

}
