package io.github.keiwebcreate.taskboard.card;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.taskboard.card.dto.CardCreateRequest;
import io.github.keiwebcreate.taskboard.card.dto.CardRequest;
import io.github.keiwebcreate.taskboard.card.dto.CardResponse;
import io.github.keiwebcreate.taskboard.column.BoardColumn;
import io.github.keiwebcreate.taskboard.column.BoardColumnRepository;
import io.github.keiwebcreate.taskboard.common.config.LimitsProperties;
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

	private final BoardColumnRepository boardColumnRepository;
	private final CardRepository cardRepository;
	private final LabelRepository labelRepository;
	private final LimitsProperties limits;

	public CardService(BoardColumnRepository boardColumnRepository, CardRepository cardRepository,
			LabelRepository labelRepository, LimitsProperties limits) {
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
		Card card = cardRepository.findByIdAndColumnBoardUserId(cardId, userId)
				.orElseThrow(NotFoundException::new);
		Long columnId = card.getColumn().getId();

		// 付いていたラベルとのつながり（card_labels の行）も一緒に消える
		cardRepository.delete(card);
		// 残ったカードを上から順に取って、0 から付け直す（消したカードはもう含まれない）
		Positions.renumber(cardRepository.findByColumnIdOrderByPosition(columnId));
	}

	// 説明が空（空白だけも含む）なら「説明なし」として null で保存する（API設計書 2.5）
	private static String emptyToNull(String description) {
		return description == null || description.isBlank() ? null : description;
	}

}
