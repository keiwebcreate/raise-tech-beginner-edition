package io.github.keiwebcreate.taskboard.board;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.taskboard.board.dto.BoardNameRequest;
import io.github.keiwebcreate.taskboard.board.dto.BoardResponse;
import io.github.keiwebcreate.taskboard.board.dto.BoardSummaryResponse;
import io.github.keiwebcreate.taskboard.board.dto.ColumnWithCardsResponse;
import io.github.keiwebcreate.taskboard.card.CardRepository;
import io.github.keiwebcreate.taskboard.card.dto.CardResponse;
import io.github.keiwebcreate.taskboard.column.BoardColumn;
import io.github.keiwebcreate.taskboard.column.BoardColumnRepository;
import io.github.keiwebcreate.taskboard.common.config.LimitsProperties;
import io.github.keiwebcreate.taskboard.common.error.LimitExceededException;
import io.github.keiwebcreate.taskboard.common.error.NotFoundException;
import io.github.keiwebcreate.taskboard.label.LabelRepository;
import io.github.keiwebcreate.taskboard.label.dto.LabelResponse;
import io.github.keiwebcreate.taskboard.user.User;
import io.github.keiwebcreate.taskboard.user.UserRepository;

/**
 * ボードの処理。
 */
@Service
public class BoardService {

	// 新しいボードに最初から入れるリスト（要件 U-02・B-03、テーブル設計書 9.1）
	private static final List<String> INITIAL_COLUMN_NAMES = List.of("未着手", "作業中", "完了");

	private final BoardRepository boardRepository;
	private final BoardColumnRepository boardColumnRepository;
	private final CardRepository cardRepository;
	private final LabelRepository labelRepository;
	private final UserRepository userRepository;
	private final LimitsProperties limits;

	// コンストラクタで受け取ると、Spring が Repository を自動で渡してくれる（DI）
	public BoardService(BoardRepository boardRepository, BoardColumnRepository boardColumnRepository,
			CardRepository cardRepository, LabelRepository labelRepository, UserRepository userRepository,
			LimitsProperties limits) {
		this.boardRepository = boardRepository;
		this.boardColumnRepository = boardColumnRepository;
		this.cardRepository = cardRepository;
		this.labelRepository = labelRepository;
		this.userRepository = userRepository;
		this.limits = limits;
	}

	/**
	 * A-22 ボードの一覧（要件 B-02）。作った順。
	 */
	@Transactional(readOnly = true)
	public List<BoardSummaryResponse> list(Long userId) {
		return boardRepository.findByUserIdOrderById(userId).stream()
				.map(BoardSummaryResponse::from)
				.toList();
	}

	/**
	 * A-23 ボードの作成（要件 B-03）。「未着手／作業中／完了」の3リストを入れる。
	 *
	 * @throws LimitExceededException ボードが上限（10個）に達しているとき
	 */
	@Transactional
	public BoardSummaryResponse create(Long userId, BoardNameRequest request) {
		// ユーザーの行に鍵をかけてから数える。2つのタブから同時に作っても、上限を超えない（詳細設計書 3.8.2）
		// ボードの数は「ボードの行」ではなく「ユーザーの行」の鍵で守る（まだないボードには鍵をかけられないため）
		User user = userRepository.findWithLockById(userId)
				.orElseThrow(NotFoundException::new);
		if (boardRepository.countByUserId(userId) >= limits.boards()) {
			throw new LimitExceededException("ボードは" + limits.boards() + "個までです");
		}
		return BoardSummaryResponse.from(createWithInitialColumns(user, request.name().strip()));
	}

	/**
	 * ボードを作り、3つのリストを入れる。ユーザー登録（A-01）とボードの作成（A-23）で使う。
	 * 呼ぶ側のトランザクションの中で動く。
	 */
	@Transactional
	public Board createWithInitialColumns(User user, String name) {
		Board board = boardRepository.save(new Board(user, name));
		for (int position = 0; position < INITIAL_COLUMN_NAMES.size(); position++) {
			boardColumnRepository.save(new BoardColumn(board, INITIAL_COLUMN_NAMES.get(position), position));
		}
		return board;
	}

	/**
	 * A-06 ボードの取得。リスト・カード・ラベルごとまとめて返す（詳細設計書 3.8.1）。
	 * SQL はカードの枚数に関係なく4回で済む。
	 *
	 * @param userId ログイン中のユーザーのID（持ち主の条件）
	 * @throws NotFoundException 他人のボード・存在しないボードのとき
	 */
	@Transactional(readOnly = true)
	public BoardResponse getBoard(Long userId, Long boardId) {
		// 1. 自分のボード
		Board board = boardRepository.findByIdAndUserId(boardId, userId)
				.orElseThrow(NotFoundException::new);
		// 2. リスト一覧（左から順）
		List<BoardColumn> columns = boardColumnRepository.findByBoardIdOrderByPosition(board.getId());
		// 3. 全カード（上から順。ラベルも一緒）を、リストのIDごとに振り分ける
		Map<Long, List<CardResponse>> cardsByColumnId = cardRepository.findAllForBoard(board.getId()).stream()
				.collect(groupingBy(card -> card.getColumn().getId(), mapping(CardResponse::from, toList())));
		// 4. ラベル一覧（作成順）。ラベルはユーザー単位で、どのボードでも同じものを使う（テーブル設計書 1章）
		List<LabelResponse> labels = labelRepository.findByUserIdOrderById(userId).stream()
				.map(LabelResponse::from)
				.toList();

		List<ColumnWithCardsResponse> columnResponses = columns.stream()
				.map(column -> new ColumnWithCardsResponse(column.getId(), column.getName(),
						cardsByColumnId.getOrDefault(column.getId(), List.of())))
				.toList();
		return new BoardResponse(board.getId(), board.getName(), labels, columnResponses);
	}

	/**
	 * A-07 ボード名の変更（要件 B-01）。
	 *
	 * @throws NotFoundException 他人のボード・存在しないボードのとき
	 */
	@Transactional
	public BoardSummaryResponse rename(Long userId, Long boardId, BoardNameRequest request) {
		Board board = boardRepository.findByIdAndUserId(boardId, userId)
				.orElseThrow(NotFoundException::new);
		board.rename(request.name().strip());
		return BoardSummaryResponse.from(board);
	}

	/**
	 * A-24 ボードの削除（要件 B-04）。最後の1つも消せる。
	 * 中のリスト・カード（とラベルとのつながり）は、DB の ON DELETE CASCADE で一緒に消える（テーブル設計書 5章）。
	 * ラベルはユーザー単位なので消えない。
	 *
	 * @throws NotFoundException 他人のボード・存在しないボードのとき
	 */
	@Transactional
	public void delete(Long userId, Long boardId) {
		// 鍵をかけて取る。同じボードにリストやカードを足している途中なら、それが終わるまで待ってから消す
		Board board = boardRepository.findWithLockByIdAndUserId(boardId, userId)
				.orElseThrow(NotFoundException::new);
		boardRepository.delete(board);
	}

}
