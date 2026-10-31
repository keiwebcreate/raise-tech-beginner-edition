package io.github.keiwebcreate.taskboard.column.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A-08 リストの追加（A-09 名前の変更でも使う）で受け取る JSON。例：{ "name": "保留" }
 * 前後の空白は Service で取り除く。@NotBlank は空白だけの名前も「空」とみなす。
 */
public record ColumnRequest(
		@NotBlank(message = "リスト名を入力してください")
		@Size(max = 50, message = "リスト名は50文字以内で入力してください")
		String name) {
}
