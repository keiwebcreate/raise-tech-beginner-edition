package io.github.keiwebcreate.taskboard.card.dto;

import java.time.LocalDate;
import java.util.List;

import io.github.keiwebcreate.taskboard.card.Card;
import io.github.keiwebcreate.taskboard.card.Level;
import io.github.keiwebcreate.taskboard.label.Label;

/**
 * API で返すカード。ラベルは中身ではなく ID だけを並べる（ラベルの中身はボードの labels にある）。
 */
public record CardResponse(
		Long id,
		String title,
		String description,
		Level importance,
		Level urgency,
		LocalDate dueDate,
		List<Long> labelIds) {

	public static CardResponse from(Card card) {
		// Set には順番がないので、毎回同じ結果になるよう ID の小さい順に並べる
		List<Long> labelIds = card.getLabels().stream()
				.map(Label::getId)
				.sorted()
				.toList();
		return new CardResponse(card.getId(), card.getTitle(), card.getDescription(),
				card.getImportance(), card.getUrgency(), card.getDueDate(), labelIds);
	}

}
