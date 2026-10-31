package io.github.keiwebcreate.taskboard.auth;

import java.util.List;
import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.taskboard.auth.dto.LoginRequest;
import io.github.keiwebcreate.taskboard.auth.dto.SignupRequest;
import io.github.keiwebcreate.taskboard.auth.dto.UserResponse;
import io.github.keiwebcreate.taskboard.board.Board;
import io.github.keiwebcreate.taskboard.board.BoardRepository;
import io.github.keiwebcreate.taskboard.column.BoardColumn;
import io.github.keiwebcreate.taskboard.column.BoardColumnRepository;
import io.github.keiwebcreate.taskboard.common.error.EmailDuplicatedException;
import io.github.keiwebcreate.taskboard.common.error.LoginFailedException;
import io.github.keiwebcreate.taskboard.user.User;
import io.github.keiwebcreate.taskboard.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ログイン関係の処理（詳細設計書 3.8.3・3.8.5・3.8.6）。
 * ログイン API を自分で作っているので、ログイン状態をセッションに保存する処理（startSession）もここで行う。
 */
@Service
public class AuthService {

	// 登録したときに作るリスト（要件 U-02、テーブル設計書 9.1）
	private static final String INITIAL_BOARD_NAME = "マイボード";
	private static final List<String> INITIAL_COLUMN_NAMES = List.of("未着手", "作業中", "完了");

	private final UserRepository userRepository;
	private final BoardRepository boardRepository;
	private final BoardColumnRepository boardColumnRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final CsrfTokenRepository csrfTokenRepository;
	private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
			.getContextHolderStrategy();

	public AuthService(UserRepository userRepository, BoardRepository boardRepository,
			BoardColumnRepository boardColumnRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository,
			CsrfTokenRepository csrfTokenRepository) {
		this.userRepository = userRepository;
		this.boardRepository = boardRepository;
		this.boardColumnRepository = boardColumnRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.csrfTokenRepository = csrfTokenRepository;
	}

	/**
	 * A-01 ユーザー登録。ボードと3つのリストも作り、そのままログインした状態にする。
	 *
	 * @throws EmailDuplicatedException 登録済みのメールアドレスのとき
	 */
	@Transactional
	public UserResponse signup(SignupRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new EmailDuplicatedException();
		}

		User user = userRepository.save(new User(email, passwordEncoder.encode(request.password()), false));
		Board board = boardRepository.save(new Board(user, INITIAL_BOARD_NAME));
		for (int position = 0; position < INITIAL_COLUMN_NAMES.size(); position++) {
			boardColumnRepository.save(new BoardColumn(board, INITIAL_COLUMN_NAMES.get(position), position));
		}

		LoginUser loginUser = new LoginUser(user);
		loginUser.eraseCredentials(); // セッションにパスワードのハッシュを残さない
		startSession(UsernamePasswordAuthenticationToken.authenticated(loginUser, null, loginUser.getAuthorities()),
				httpRequest, httpResponse);
		return UserResponse.from(loginUser);
	}

	/**
	 * A-02 ログイン。
	 *
	 * @throws LoginFailedException メールアドレスかパスワードが違うとき（どちらが違うかは教えない）
	 */
	@Transactional(readOnly = true)
	public UserResponse login(LoginRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		String email = normalizeEmail(request.email());
		String password = request.password() == null ? "" : request.password();
		Authentication authentication;
		try {
			// LoginUserDetailsService でユーザーを探し、BCrypt でパスワードを照合する
			authentication = authenticationManager
					.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
		} catch (AuthenticationException e) {
			throw new LoginFailedException();
		}
		startSession(authentication, httpRequest, httpResponse);
		return UserResponse.from((LoginUser) authentication.getPrincipal());
	}

	/**
	 * A-04 ログアウト。セッションを破棄する。すでにセッションが切れていても、何もせずに終わる。
	 * ゲストのデータの削除は、ゲストを作るときに足す。
	 */
	public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		Authentication authentication = securityContextHolderStrategy.getContext().getAuthentication();
		// セッションを破棄し、CSRF トークンとセッションの Cookie も消す
		new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, authentication);
		new CsrfLogoutHandler(csrfTokenRepository).logout(httpRequest, httpResponse, authentication);
		new CookieClearingLogoutHandler("SESSION").logout(httpRequest, httpResponse, authentication);
	}

	/**
	 * ログインした状態にする（ログイン・登録のあとで使う）。
	 * 1. セッションIDを新しくする（セッション固定攻撃の対策。API設計書 2.2）
	 * 2. CSRF トークンを作り直す（API設計書 2.3）
	 * 3. ログイン状態をセッションに保存する
	 */
	private void startSession(Authentication authentication, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, httpRequest, httpResponse);
		new CsrfAuthenticationStrategy(csrfTokenRepository).onAuthentication(authentication, httpRequest,
				httpResponse);

		SecurityContext context = securityContextHolderStrategy.createEmptyContext();
		context.setAuthentication(authentication);
		securityContextHolderStrategy.setContext(context);
		securityContextRepository.saveContext(context, httpRequest, httpResponse);
	}

	// 前後の空白を取り除き、小文字にそろえる（要件 3章：Taro@example.com と taro@example.com は同じ人）
	private static String normalizeEmail(String email) {
		return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
	}

}
