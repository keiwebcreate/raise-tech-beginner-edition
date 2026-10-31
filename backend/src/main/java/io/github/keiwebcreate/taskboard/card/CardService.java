package io.github.keiwebcreate.taskboard.card;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.taskboard.card.dto.CardCreateRequest;
import io.github.keiwebcreate.taskboard.card.dto.CardResponse;
import io.github.keiwebcreate.taskboard.column.BoardColumn;
import io.github.keiwebcreate.taskboard.column.BoardColumnRepository;
import io.github.keiwebcreate.taskboard.common.config.LimitsProperties;
import io.github.keiwebcreate.taskboard.common.error.LimitExceededException;
import io.github.keiwebcreate.taskboard.common.error.NotFoundException;

/**
 * カードの処理。
 */
@Service
public class CardService {

	private final BoardColumnRepository boardColumnRepository;
	private final CardRepository cardRepository;
	private final LimitsProperties limits;

	public CardService(BoardColumnRepository boardColumnRepository, CardRepository cardRepository,
			LimitsProperties limits) {
		this.boardColumnRepository = boardColumnRepository;
		this.cardRepository = cardRepository;
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

}
