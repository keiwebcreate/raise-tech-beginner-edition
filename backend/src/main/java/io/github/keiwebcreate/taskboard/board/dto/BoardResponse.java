package io.github.keiwebcreate.taskboard.board.dto;

import java.util.List;

import io.github.keiwebcreate.taskboard.label.dto.LabelResponse;

/**
 * ボードの取得（A-06 GET /api/board）で返す JSON の形（API設計書 5.2）。
 * labels は作成順、columns は左から順に並べる。
 */
public record BoardResponse(
		Long id,
		String name,
		List<LabelResponse> labels,
		List<ColumnWithCardsResponse> columns) {
}
