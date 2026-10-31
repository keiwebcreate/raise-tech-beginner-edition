package io.github.keiwebcreate.taskboard.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A-12 カードの追加で受け取る JSON。タイトルだけ。例：{ "title": "牛乳を買う" }
 * 前後の空白は Service で取り除く。
 */
public record CardCreateRequest(
		@NotBlank(message = "タイトルを入力してください")
		@Size(max = 100, message = "タイトルは100文字以内で入力してください")
		String title) {
}
