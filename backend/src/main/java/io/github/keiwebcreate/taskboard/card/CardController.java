package io.github.keiwebcreate.taskboard.card;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.keiwebcreate.taskboard.auth.LoginUser;
import io.github.keiwebcreate.taskboard.card.dto.CardCreateRequest;
import io.github.keiwebcreate.taskboard.card.dto.CardRequest;
import io.github.keiwebcreate.taskboard.card.dto.CardResponse;
import jakarta.validation.Valid;

/**
 * カードの API（API設計書 5.4）。
 */
@RestController
public class CardController {

	private final CardService cardService;

	public CardController(CardService cardService) {
		this.cardService = cardService;
	}

	// A-12 カードの追加。どのリストに足すかを URL で指定する
	@PostMapping("/api/columns/{columnId}/cards")
	@ResponseStatus(HttpStatus.CREATED)
	public CardResponse add(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long columnId,
			@Valid @RequestBody CardCreateRequest request) {
		return cardService.add(loginUser.getId(), columnId, request);
	}

	// A-13 カードの保存（全項目をまとめて置き換えるので PUT）
	@PutMapping("/api/cards/{cardId}")
	public CardResponse save(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long cardId,
			@Valid @RequestBody CardRequest request) {
		return cardService.save(loginUser.getId(), cardId, request);
	}

	// A-14 カードの削除。成功したら 204（中身なし）
	@DeleteMapping("/api/cards/{cardId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long cardId) {
		cardService.delete(loginUser.getId(), cardId);
	}

}
