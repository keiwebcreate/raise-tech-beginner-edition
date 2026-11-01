package io.github.keiwebcreate.taskboard.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.keiwebcreate.taskboard.auth.dto.EmailChangeRequest;
import io.github.keiwebcreate.taskboard.auth.dto.LoginRequest;
import io.github.keiwebcreate.taskboard.auth.dto.PasswordChangeRequest;
import io.github.keiwebcreate.taskboard.auth.dto.SignupRequest;
import io.github.keiwebcreate.taskboard.auth.dto.UserResponse;
import io.github.keiwebcreate.taskboard.auth.dto.WithdrawRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * ログイン関係の API（API設計書 5.1）。処理の中身は AuthService に任せる。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	// A-01 ユーザー登録
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse signup(@Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		return authService.signup(request, httpRequest, httpResponse);
	}

	// A-02 ログイン
	@PostMapping("/login")
	public UserResponse login(@RequestBody LoginRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		return authService.login(request, httpRequest, httpResponse);
	}

	// A-03 ゲストログイン
	@PostMapping("/guest")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse guest(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		return authService.guest(httpRequest, httpResponse);
	}

	// A-04 ログアウト
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		authService.logout(httpRequest, httpResponse);
	}

	// A-19 退会
	@PostMapping("/withdraw")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void withdraw(@AuthenticationPrincipal LoginUser loginUser, @RequestBody WithdrawRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		authService.withdraw(loginUser, request, httpRequest, httpResponse);
	}

	// A-20 メールアドレスの変更
	@PutMapping("/email")
	public UserResponse changeEmail(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody EmailChangeRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		return authService.changeEmail(loginUser, request, httpRequest, httpResponse);
	}

	// A-21 パスワードの変更
	@PutMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody PasswordChangeRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		authService.changePassword(loginUser, request, httpRequest, httpResponse);
	}

	// A-05 ログイン中のユーザー
	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal LoginUser loginUser, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		return authService.me(loginUser, httpRequest, httpResponse);
	}

}
