package io.github.keiwebcreate.taskboard.card;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.taskboard.board.BoardRepository;
import io.github.keiwebcreate.taskboard.card.dto.CardCreateRequest;
import io.github.keiwebcreate.taskboard.card.dto.CardPositionRequest;
import io.github.keiwebcreate.taskboard.card.dto.CardRequest;
import io.github.keiwebcreate.taskboard.card.dto.CardResponse;
import io.github.keiwebcreate.taskboard.column.BoardColumn;
import io.github.keiwebcreate.taskboard.column.BoardColumnRepository;
import io.github.keiwebcreate.taskboard.common.config.LimitsProperties;
import io.github.keiwebcreate.taskboard.common.error.InvalidPositionException;
import io.github.keiwebcreate.taskboard.common.error.LimitExceededException;
import io.github.keiwebcreate.taskboard.common.error.NotFoundException;
import io.github.keiwebcreate.taskboard.common.position.Positions;
import io.github.keiwebcreate.taskboard.label.Label;
import io.github.keiwebcreate.taskboard.label.LabelRepository;

/**
 * カードの処理。
 */
@Service
public class CardService {

	private final BoardRepository boardRepository;
	private final BoardColumnRepository boardColumnRepository;
	private final CardRepository cardRepository;
	private final LabelRepository labelRepository;
	private final LimitsProperties limits;

	public CardService(BoardRepository boardRepository, BoardColumnRepository boardColumnRepository,
			CardRepository cardRepository, LabelRepository labelRepository, LimitsProperties limits) {
		this.boardRepository = boardRepository;
		this.boardColumnRepository = boardColumnRepository;
		this.cardRepository = cardRepository;
		this.labelRepository = labelRepository;
		this.limits = limits;
	}

	/**
	 * A-12 カードの追加。リストの一番下に足す（要件 C-01）。重要度・緊急度は「中」、ほかは空。
	 *
	 * @throws NotFoundException      他人のリスト・存在しないリストのとき
	 * @throws LimitExceededException リストのカードが上限（100枚）に達しているとき
	 */
	@Transactional
	public CardResponse add(Long userId, Long columnId, CardCreateRequest request) {
		lockBoard(userId);
		// 持ち主の条件付きで探す。他人のリストなら見つからないので 404 になる
		BoardColumn column = boardColumnRepository.findByIdAndBoardUserId(columnId, userId)
				.orElseThrow(NotFoundException::new);

		long count = cardRepository.countByColumnId(column.getId());
		if (count >= limits.cardsPerColumn()) {
			throw new LimitExceededException("このリストのカードは" + limits.cardsPerColumn() + "枚までです");
		}

		// 並び順は0からの連番なので、今の枚数がそのまま一番下の位置になる
		Card card = new Card(column, request.title().strip(), (int) count);
		return CardResponse.from(cardRepository.save(card));
	}

	/**
	 * A-13 カードの保存。カード詳細の全項目をまとめて置き換える（要件 C-02）。
	 *
	 * @throws NotFoundException 他人のカード・存在しないカードのとき。labelIds に他人・存在しないラベルがあるときも
	 */
	@Transactional
	public CardResponse save(Long userId, Long cardId, CardRequest request) {
		Card card = cardRepository.findByIdAndColumnBoardUserId(cardId, userId)
				.orElseThrow(NotFoundException::new);

		// 重複した ID は1つとみなす（API設計書 A-13）
		Set<Long> labelIds = new HashSet<>(request.labelIds());
		List<Label> labels = labelRepository.findAllByIdInAndUserId(labelIds, userId);
		if (labels.size() < labelIds.size()) {
			throw new NotFoundException();
		}

		card.update(
				request.title().strip(),
				emptyToNull(request.description()),
				request.importance(),
				request.urgency(),
				request.dueDate(),
				new HashSet<>(labels));
		// @Transactional のメソッドが終わるときに、変えた値が UPDATE 文で DB に書き込まれる（save を呼ばなくてよい）
		return CardResponse.from(card);
	}

	/**
	 * A-14 カードの削除（要件 C-03）。そのリストに残ったカードの並び順を振り直す。
	 *
	 * @throws NotFoundException 他人のカード・存在しないカードのとき
	 */
	@Transactional
	public void delete(Long userId, Long cardId) {
		lockBoard(userId);
		Card card = cardRepository.findByIdAndColumnBoardUserId(cardId, userId)
				.orElseThrow(NotFoundException::new);
		Long columnId = card.getColumn().getId();

		// 付いていたラベルとのつながり（card_labels の行）も一緒に消える
		cardRepository.delete(card);
		// 残ったカードを上から順に取って、0 から付け直す（消したカードはもう含まれない）
		Positions.renumber(cardRepository.findByColumnIdOrderByPosition(columnId));
	}

	/**
	 * A-15 カードの並び替え・別のリストへの移動（要件 C-04）。
	 * position は、移動したあとの移動先のリストで上から何番目か（0から）。
	 * 例：リスト「A, B」へ、別のリストのカード X を 1 で移す → A, X, B
	 *
	 * @throws NotFoundException        他人・存在しないカード、または他人・存在しない移動先のリストのとき
	 * @throws InvalidPositionException position が範囲の外のとき（同じリスト内は 0〜枚数−1、別のリストへは 0〜枚数）
	 * @throws LimitExceededException   別のリストへ移すとき、移動先がすでに上限（100枚）のとき
	 */
	@Transactional
	public void move(Long userId, Long cardId, CardPositionRequest request) {
		lockBoard(userId);
		Card card = cardRepository.findByIdAndColumnBoardUserId(cardId, userId)
				.orElseThrow(NotFoundException::new);
		// 移動先のリストも、持ち主の条件付きで探す。他人のリストへは移せない
		BoardColumn target = boardColumnRepository.findByIdAndBoardUserId(request.columnId(), userId)
				.orElseThrow(NotFoundException::new);
		Long sourceId = card.getColumn().getId();
		boolean sameColumn = sourceId.equals(target.getId());

		List<Card> targetCards = new ArrayList<>(cardRepository.findByColumnIdOrderByPosition(target.getId()));
		// 同じリスト内なら、動かすカード自身がもう数に入っているので、一番下は「枚数−1」になる
		int last = sameColumn ? targetCards.size() - 1 : targetCards.size();
		int position = request.position();
		if (position < 0 || position > last) {
			throw new InvalidPositionException();
		}

		if (sameColumn) {
			targetCards.removeIf(c -> c.getId().equals(cardId));
		} else {
			if (targetCards.size() >= limits.cardsPerColumn()) {
				throw new LimitExceededException("このリストのカードは" + limits.cardsPerColumn() + "枚までです");
			}
			// 移動元のリストは、動かすカードを除いて付け直す（抜けた番号を詰める）
			List<Card> sourceCards = new ArrayList<>(cardRepository.findByColumnIdOrderByPosition(sourceId));
			sourceCards.removeIf(c -> c.getId().equals(cardId));
			Positions.renumber(sourceCards);
			card.moveTo(target);
		}

		// Java の List の上で「指定の位置に差し込む」。そのあと先頭から 0, 1, 2 … と付け直す（詳細設計書 3.8.2）
		targetCards.add(position, card);
		Positions.renumber(targetCards);
	}

	// 自分のボードに鍵をかける（BoardRepository.findWithLockByUserId）。ほかのデータを読む前に、最初に呼ぶ
	private void lockBoard(Long userId) {
		boardRepository.findWithLockByUserId(userId)
				.orElseThrow(NotFoundException::new);
	}

	// 説明が空（空白だけも含む）なら「説明なし」として null で保存する（API設計書 2.5）
	private static String emptyToNull(String description) {
		return description == null || description.isBlank() ? null : description;
	}

}
