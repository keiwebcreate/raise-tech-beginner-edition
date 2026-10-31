package io.github.keiwebcreate.taskboard.card.dto;

import jakarta.validation.constraints.NotNull;

/**
 * A-15 カードの並び替え・移動で受け取る JSON。例：{ "columnId": 11, "position": 0 }
 * columnId は移動先のリスト（同じリスト内の並び替えなら今のリスト）。
 * position は、移動したあとの移動先のリストで上から何番目か（0から）。範囲の確認は Service で行う。
 */
public record CardPositionRequest(
		@NotNull(message = "移動先のリストを指定してください")
		Long columnId,
		@NotNull(message = "位置を指定してください")
		Integer position) {
}
