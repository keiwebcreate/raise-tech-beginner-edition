package io.github.keiwebcreate.taskboard.column;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.taskboard.board.Board;
import io.github.keiwebcreate.taskboard.board.BoardRepository;
import io.github.keiwebcreate.taskboard.column.dto.ColumnRequest;
import io.github.keiwebcreate.taskboard.column.dto.ColumnResponse;
import io.github.keiwebcreate.taskboard.common.config.LimitsProperties;
import io.github.keiwebcreate.taskboard.common.error.LimitExceededException;
import io.github.keiwebcreate.taskboard.common.error.NotFoundException;

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
		Board board = boardRepository.findByUserId(userId)
				.orElseThrow(NotFoundException::new);

		long count = boardColumnRepository.countByBoardId(board.getId());
		if (count >= limits.columns()) {
			throw new LimitExceededException("リストは" + limits.columns() + "個までです");
		}

		// 並び順は0からの連番なので、今の数がそのまま右端の位置になる
		BoardColumn column = new BoardColumn(board, request.name().strip(), (int) count);
		return ColumnResponse.from(boardColumnRepository.save(column));
	}

}
