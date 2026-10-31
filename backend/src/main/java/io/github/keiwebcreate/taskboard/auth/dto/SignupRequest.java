package io.github.keiwebcreate.taskboard.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A-01 ユーザー登録で受け取る JSON。例：{ "email": "taro@example.com", "password": "password123" }
 * 入力のルールは要件 3章、メッセージは画面設計書 7章のとおり。
 */
public record SignupRequest(
		@NotBlank(message = "メールアドレスを入力してください")
		@Email(message = "メールアドレスの形式が正しくありません")
		@Size(max = 254, message = "メールアドレスは254文字以内で入力してください")
		String email,

		// 8〜64文字の、半角の英数字と記号（! から ~ まで）だけ
		@NotBlank(message = "パスワードを入力してください")
		@Pattern(regexp = "^[\\x21-\\x7E]{8,64}$", message = "パスワードは8〜64文字の半角英数字・記号で入力してください")
		String password) {
}
