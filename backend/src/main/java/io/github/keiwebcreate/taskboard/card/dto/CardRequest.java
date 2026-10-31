package io.github.keiwebcreate.taskboard.card.dto;

import java.time.LocalDate;
import java.util.List;

import io.github.keiwebcreate.taskboard.card.Level;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A-13 カードの保存で受け取る JSON（カード詳細の全項目。API設計書 A-13）。
 * importance・urgency に HIGH・MEDIUM・LOW 以外が来たら、JSON を読む時点で 400 VALIDATION_ERROR になる。
 */
public record CardRequest(
		@NotBlank(message = "タイトルを入力してください")
		@Size(max = 100, message = "タイトルは100文字以内で入力してください")
		String title,

		@Size(max = 2000, message = "説明は2000文字以内で入力してください")
		String description,

		@NotNull(message = "重要度を選んでください")
		Level importance,

		@NotNull(message = "緊急度を選んでください")
		Level urgency,

		// 期限なしは null
		LocalDate dueDate,

		// 保存後に付いているラベルの ID。空の配列ならすべて外す
		@NotNull(message = "ラベルを指定してください")
		List<Long> labelIds) {
}
