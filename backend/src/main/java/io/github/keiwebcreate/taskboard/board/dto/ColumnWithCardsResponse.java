package io.github.keiwebcreate.taskboard.board.dto;

import java.util.List;

import io.github.keiwebcreate.taskboard.card.dto.CardResponse;

/**
 * ボードの取得（A-06）で返すリスト。中のカードを上から順に持つ。
 */
public record ColumnWithCardsResponse(Long id, String name, List<CardResponse> cards) {
}
