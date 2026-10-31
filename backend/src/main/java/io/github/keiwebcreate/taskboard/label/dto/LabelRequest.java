package io.github.keiwebcreate.taskboard.label.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A-16 ラベルの追加・A-17 ラベルの変更で受け取る JSON。例：{ "name": "急ぎ", "color": "#f76b15" }
 * 前後の空白は Service で取り除く。色が8色のどれかは Service で確かめる（LabelColor）。
 */
public record LabelRequest(
		@NotBlank(message = "ラベル名を入力してください")
		@Size(max = 20, message = "ラベル名は20文字以内で入力してください")
		String name,

		@NotNull(message = "色を選んでください")
		String color) {
}
