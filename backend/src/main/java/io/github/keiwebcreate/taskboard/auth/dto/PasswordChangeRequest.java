package io.github.keiwebcreate.taskboard.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * A-21 パスワードの変更で受け取る JSON。例：{ "currentPassword": "password123", "newPassword": "newpass456" }
 * 新しいパスワードの入力ルールは登録（SignupRequest）と同じ。
 * 今のパスワードには入力チェックを付けない（EmailChangeRequest と同じ理由）。
 * 新しいパスワード（確認）は画面側だけでチェックするので、送らない（登録と同じ）。
 */
public record PasswordChangeRequest(
		String currentPassword,

		// 8〜64文字の、半角の英数字と記号（! から ~ まで）だけ
		@NotBlank(message = "新しいパスワードを入力してください")
		@Pattern(regexp = "^[\\x21-\\x7E]{8,64}$", message = "パスワードは8〜64文字の半角英数字・記号で入力してください")
		String newPassword) {
}
