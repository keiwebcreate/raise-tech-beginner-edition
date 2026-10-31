package io.github.keiwebcreate.taskboard.column.dto;

import jakarta.validation.constraints.NotNull;

/**
 * A-11 リストの並び替えで受け取る JSON。例：{ "position": 2 }
 * position は、移動したあとの並びで左から何番目か（0から）。範囲の確認は Service で行う（範囲外は INVALID_POSITION）。
 * int ではなく Integer にして、送られてこなかったとき（null）を 0 と取り違えないようにする。
 */
public record PositionRequest(
		@NotNull(message = "位置を指定してください")
		Integer position) {
}
