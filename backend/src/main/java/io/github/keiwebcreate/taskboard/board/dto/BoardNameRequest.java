package io.github.keiwebcreate.taskboard.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A-07 ボード名の変更で受け取る JSON。例：{ "name": "仕事用ボード" }
 * 前後の空白は Service で取り除く。
 */
public record BoardNameRequest(
		@NotBlank(message = "ボード名を入力してください")
		@Size(max = 50, message = "ボード名は50文字以内で入力してください")
		String name) {
}
