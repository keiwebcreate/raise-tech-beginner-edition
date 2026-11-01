package io.github.keiwebcreate.taskboard.board;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

/**
 * boards テーブルとのやりとり。
 * 1人が複数のボードを持てる（要件 B-02）。1件を探すときは必ず「ボード ID ＋ 持ち主」の条件で探す（詳細設計書 3.5）。
 */
public interface BoardRepository extends JpaRepository<Board, Long> {

	// SELECT ... FROM boards WHERE user_id = ? ORDER BY id（自分のボードの一覧。ID は作った順に増えるので、作った順になる）
	List<Board> findByUserIdOrderById(Long userId);

	// 自分のボードの数（上限の確認に使う）
	long countByUserId(Long userId);

	// 自分のボード1件。他人のボードの ID を指定されても見つからない
	Optional<Board> findByIdAndUserId(Long boardId, Long userId);

	/*
	 * ここから下は、ボードに鍵をかけて取るメソッド（SELECT ... FOR UPDATE。詳細設計書 3.8.2）。
	 * 並び順や数を変える処理（リスト・カードの追加・削除・並び替え）の最初に呼ぶ。
	 * 同じボードへの別の処理は、このトランザクションが終わるまで待つので、
	 * 2つのタブから同時に操作しても、番号が重なったり上限を超えたりしない。
	 * どれも持ち主の条件が入っているので、他人のボード・リスト・カードなら見つからない。
	 */

	// ボードの ID から（リストの追加・ボードの削除）
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Board> findWithLockByIdAndUserId(Long boardId, Long userId);

	// リストの ID から、そのリストのボードに（リストの変更・削除・並び替え、カードの追加）
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select b from Board b
			where b.user.id = :userId
			and b.id = (select c.board.id from BoardColumn c where c.id = :columnId)
			""")
	Optional<Board> findWithLockByColumnIdAndUserId(Long columnId, Long userId);

	// カードの ID から、そのカードのボードに（カードの削除・並び替え）
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select b from Board b
			where b.user.id = :userId
			and b.id = (select c.column.board.id from Card c where c.id = :cardId)
			""")
	Optional<Board> findWithLockByCardIdAndUserId(Long cardId, Long userId);

}
