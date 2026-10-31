package io.github.keiwebcreate.taskboard.board;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.taskboard.board.dto.BoardResponse;
import io.github.keiwebcreate.taskboard.board.dto.ColumnWithCardsResponse;
import io.github.keiwebcreate.taskboard.card.CardRepository;
import io.github.keiwebcreate.taskboard.card.dto.CardResponse;
import io.github.keiwebcreate.taskboard.column.BoardColumn;
import io.github.keiwebcreate.taskboard.column.BoardColumnRepository;
import io.github.keiwebcreate.taskboard.common.error.NotFoundException;
import io.github.keiwebcreate.taskboard.label.LabelRepository;
import io.github.keiwebcreate.taskboard.label.dto.LabelResponse;

/**
 * ボードの処理。
 */
@Service
public class BoardService {

	private final BoardRepository boardRepository;
	private final BoardColumnRepository boardColumnRepository;
	private final CardRepository cardRepository;
	private final LabelRepository labelRepository;

	// コンストラクタで受け取ると、Spring が Repository を自動で渡してくれる（DI）
	public BoardService(BoardRepository boardRepository, BoardColumnRepository boardColumnRepository,
			CardRepository cardRepository, LabelRepository labelRepository) {
		this.boardRepository = boardRepository;
		this.boardColumnRepository = boardColumnRepository;
		this.cardRepository = cardRepository;
		this.labelRepository = labelRepository;
	}

	/**
	 * ログイン中のユーザーのボードを、リスト・カード・ラベルごとまとめて返す（A-06。詳細設計書 3.8.1）。
	 * SQL はカードの枚数に関係なく4回で済む。
	 *
	 * @param userId ログイン中のユーザーのID（持ち主の条件）
	 * @throws NotFoundException ボードがないとき
	 */
	@Transactional(readOnly = true)
	public BoardResponse getBoard(Long userId) {
		// 1. 自分のボード
		Board board = boardRepository.findByUserId(userId)
				.orElseThrow(NotFoundException::new);
		// 2. リスト一覧（左から順）
		List<BoardColumn> columns = boardColumnRepository.findByBoardIdOrderByPosition(board.getId());
		// 3. 全カード（上から順。ラベルも一緒）を、リストのIDごとに振り分ける
		Map<Long, List<CardResponse>> cardsByColumnId = cardRepository.findAllForBoard(board.getId()).stream()
				.collect(groupingBy(card -> card.getColumn().getId(), mapping(CardResponse::from, toList())));
		// 4. ラベル一覧（作成順）
		List<LabelResponse> labels = labelRepository.findByUserIdOrderById(userId).stream()
				.map(LabelResponse::from)
				.toList();

		List<ColumnWithCardsResponse> columnResponses = columns.stream()
				.map(column -> new ColumnWithCardsResponse(column.getId(), column.getName(),
						cardsByColumnId.getOrDefault(column.getId(), List.of())))
				.toList();
		return new BoardResponse(board.getId(), board.getName(), labels, columnResponses);
	}

}
