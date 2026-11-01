package io.github.keiwebcreate.taskboard.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A-20 メールアドレスの変更で受け取る JSON。例：{ "email": "new@example.com", "currentPassword": "password123" }
 * 新しいメールアドレスの入力ルールは登録（SignupRequest）と同じ。
 * 今のパスワードには入力チェックを付けない。空のときも、違うときと同じ「パスワードが正しくありません」にする（退会と同じ）。
 */
public record EmailChangeRequest(
		@NotBlank(message = "メールアドレスを入力してください")
		@Email(message = "メールアドレスの形式が正しくありません")
		@Size(max = 254, message = "メールアドレスは254文字以内で入力してください")
		String email,

		String currentPassword) {
}
