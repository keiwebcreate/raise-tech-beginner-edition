package io.github.keiwebcreate.taskboard.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import io.github.keiwebcreate.taskboard.user.UserRepository;

/**
 * ログインのときに、メールアドレスからユーザーを探す（Spring Security が呼ぶ）。
 * 見つからなくても、Spring Security が「パスワード違い」と同じ扱いにしてくれる（どちらが違うかを教えない）。
 */
@Service
public class LoginUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public LoginUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		return userRepository.findByEmail(email)
				.map(LoginUser::new)
				.orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません"));
	}

}
