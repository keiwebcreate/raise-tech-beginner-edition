package io.github.keiwebcreate.taskboard.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import io.github.keiwebcreate.taskboard.user.User;

/**
 * ログイン中のユーザー（詳細設計書 3.8.3）。セッションに保存される。
 * Controller は @AuthenticationPrincipal LoginUser で受け取り、getId() を Service に渡す。
 */
public class LoginUser implements UserDetails, CredentialsContainer {

	private final Long id;
	private final String email;
	private final boolean guest;
	// ログインの照合にだけ使う。照合が終わったら消す（eraseCredentials）ので、セッションには残らない
	private String passwordHash;

	public LoginUser(User user) {
		this.id = user.getId();
		this.email = user.getEmail();
		this.guest = user.isGuest();
		this.passwordHash = user.getPasswordHash();
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public boolean isGuest() {
		return guest;
	}

	// Spring Security が「ユーザー名」として使う値。このアプリではメールアドレス
	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	// 権限（管理者など）は使わないので空
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of();
	}

	@Override
	public void eraseCredentials() {
		this.passwordHash = null;
	}

}
