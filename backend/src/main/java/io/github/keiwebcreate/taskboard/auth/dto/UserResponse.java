package io.github.keiwebcreate.taskboard.auth.dto;

import io.github.keiwebcreate.taskboard.auth.LoginUser;

/**
 * API で返すユーザー情報。例：{ "id": 1, "email": "taro@example.com", "isGuest": false }
 * パスワードは返さない。
 */
public record UserResponse(Long id, String email, boolean isGuest) {

	public static UserResponse from(LoginUser loginUser) {
		return new UserResponse(loginUser.getId(), loginUser.getEmail(), loginUser.isGuest());
	}

}
