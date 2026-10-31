package io.github.keiwebcreate.taskboard.column;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.taskboard.board.Board;
import io.github.keiwebcreate.taskboard.board.BoardRepository;
import io.github.keiwebcreate.taskboard.column.dto.ColumnRequest;
import io.github.keiwebcreate.taskboard.column.dto.ColumnResponse;
import io.github.keiwebcreate.taskboard.common.config.LimitsProperties;
import io.github.keiwebcreate.taskboard.common.error.InvalidPositionException;
import io.github.keiwebcreate.taskboard.common.error.LimitExceededException;
import io.github.keiwebcreate.taskboard.common.error.NotFoundException;
import io.github.keiwebcreate.taskboard.common.position.Positions;

/**
 * リストの処理。
 */
@Service
public class ColumnService {

	private final BoardRepository boardRepository;
	private final BoardColumnRepository boardColumnRepository;
	private final LimitsProperties limits;

	public ColumnService(BoardRepository boardRepository, BoardColumnRepository boardColumnRepository,
			LimitsProperties limits) {
		this.boardRepository = boardRepository;
		this.boardColumnRepository = boardColumnRepository;
		this.limits = limits;
	}

	/**
	 * A-08 リストの追加。ログイン中のユーザーのボードの右端に足す（要件 L-02）。
	 *
	 * @throws LimitExceededException リストが上限（20個）に達しているとき
	 */
	@Transactional
	public ColumnResponse add(Long userId, ColumnRequest request) {
		Board board = lockBoard(userId);

		long count = boardColumnRepository.countByBoardId(board.getId());
		if (count >= limits.columns()) {
			throw new LimitExceededException("リストは" + limits.columns() + "個までです");
		}

		// 並び順は0からの連番なので、今の数がそのまま右端の位置になる
		BoardColumn column = new BoardColumn(board, request.name().strip(), (int) count);
		return ColumnResponse.from(boardColumnRepository.save(column));
	}

	/**
	 * A-09 リスト名の変更（要件 L-03）。
	 *
	 * @throws NotFoundException 他人のリスト・存在しないリストのとき
	 */
	@Transactional
	public ColumnResponse rename(Long userId, Long columnId, ColumnRequest request) {
		BoardColumn column = boardColumnRepository.findByIdAndBoardUserId(columnId, userId)
				.orElseThrow(NotFoundException::new);
		column.rename(request.name().strip());
		return ColumnResponse.from(column);
	}

	/**
	 * A-10 リストの削除（要件 L-04）。中のカードも消え、残ったリストの並び順を振り直す。
	 * リストは0個になってもよい。
	 *
	 * @throws NotFoundException 他人のリスト・存在しないリストのとき
	 */
	@Transactional
	public void delete(Long userId, Long columnId) {
		lockBoard(userId);
		BoardColumn column = boardColumnRepository.findByIdAndBoardUserId(columnId, userId)
				.orElseThrow(NotFoundException::new);
		Long boardId = column.getBoard().getId();

		// 中のカード（とラベルとのつながり）は、DB の ON DELETE CASCADE で一緒に消える（テーブル設計書 5章）
		boardColumnRepository.delete(column);
		Positions.renumber(boardColumnRepository.findByBoardIdOrderByPosition(boardId));
	}

	/**
	 * A-11 リストの並び替え（要件 L-05）。position は、移動したあとの並びで左から何番目か（0から）。
	 * 例：「A, B, C」の A を 2 に動かす → B, C, A
	 *
	 * @throws NotFoundException        他人のリスト・存在しないリストのとき
	 * @throws InvalidPositionException position が 0〜（リストの数−1）の外のとき
	 */
	@Transactional
	public void move(Long userId, Long columnId, int position) {
		lockBoard(userId);
		BoardColumn column = boardColumnRepository.findByIdAndBoardUserId(columnId, userId)
				.orElseThrow(NotFoundException::new);
		List<BoardColumn> columns = new ArrayList<>(
				boardColumnRepository.findByBoardIdOrderByPosition(column.getBoard().getId()));
		if (position < 0 || position >= columns.size()) {
			throw new InvalidPositionException();
		}

		// Java の List の上で「取り除いて、指定の位置に差し込む」。そのあと先頭から 0, 1, 2 … と付け直す（詳細設計書 3.8.2）
		columns.removeIf(c -> c.getId().equals(columnId));
		columns.add(position, column);
		Positions.renumber(columns);
	}

	// 自分のボードに鍵をかける（BoardRepository.findWithLockByUserId）。ほかのデータを読む前に、最初に呼ぶ
	private Board lockBoard(Long userId) {
		return boardRepository.findWithLockByUserId(userId)
				.orElseThrow(NotFoundException::new);
	}

}
