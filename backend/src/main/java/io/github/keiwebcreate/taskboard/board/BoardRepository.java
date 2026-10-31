package io.github.keiwebcreate.taskboard.board;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

/**
 * boards テーブルとのやりとり。
 */
public interface BoardRepository extends JpaRepository<Board, Long> {

	// SELECT ... FROM boards WHERE user_id = ?（自分のボード）
	Optional<Board> findByUserId(Long userId);

	/**
	 * SELECT ... FROM boards WHERE user_id = ? FOR UPDATE（自分のボードに鍵をかけて取る。詳細設計書 3.8.2）
	 * 並び順や数を変える処理（リスト・カードの追加・削除・並び替え）の最初に呼ぶ。
	 * 同じボードへの別の処理は、このトランザクションが終わるまで待つので、
	 * 2つのタブから同時に操作しても、番号が重なったり上限を超えたりしない。
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Board> findWithLockByUserId(Long userId);

}
