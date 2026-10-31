package io.github.keiwebcreate.taskboard.auth;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InsufficientAuthenticationException;
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
	private final GuestSampleData guestSampleData;
	private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
			.getContextHolderStrategy();

	public AuthService(UserRepository userRepository, BoardRepository boardRepository,
			BoardColumnRepository boardColumnRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository,
			CsrfTokenRepository csrfTokenRepository, GuestSampleData guestSampleData) {
		this.userRepository = userRepository;
		this.boardRepository = boardRepository;
		this.boardColumnRepository = boardColumnRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.csrfTokenRepository = csrfTokenRepository;
		this.guestSampleData = guestSampleData;
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

		User user;
		try {
			user = userRepository.save(new User(email, passwordEncoder.encode(request.password()), false));
		} catch (DataIntegrityViolationException e) {
			// 上の確認と保存の間に、同じメールアドレスで登録された（ほぼ同時の登録）。
			// DB の一意制約（uk_users_email）が2人目を止めるので、確認で見つかったときと同じ 409 にする
			throw new EmailDuplicatedException();
		}
		Board board = boardRepository.save(new Board(user, INITIAL_BOARD_NAME));
		for (int position = 0; position < INITIAL_COLUMN_NAMES.size(); position++) {
			boardColumnRepository.save(new BoardColumn(board, INITIAL_COLUMN_NAMES.get(position), position));
		}

		return startSession(user, httpRequest, httpResponse);
	}

	/**
	 * A-03 ゲストログイン（要件 U-06）。押すたびに新しいゲストを作り、サンプルデータの入ったボードを用意して、ログインした状態にする。
	 * メールアドレスとパスワードはランダム。パスワードは誰も知らないので、ゲストのメールアドレスではログインできない（A-02）。
	 */
	@Transactional
	public UserResponse guest(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		// UUID は乱数で作る128ビットの値なので、重なったり当てられたりしない
		String email = "guest-" + UUID.randomUUID() + "@guest.local";
		String password = UUID.randomUUID().toString();
		User user = userRepository.save(new User(email, passwordEncoder.encode(password), true));
		guestSampleData.create(user);
		return startSession(user, httpRequest, httpResponse);
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
		// 照合に成功すると、中身は LoginUserDetailsService が返した LoginUser になる（型の上では null もありうるので確かめる）
		if (!(authentication.getPrincipal() instanceof LoginUser loginUser)) {
			throw new LoginFailedException();
		}
		startSession(authentication, httpRequest, httpResponse);
		return UserResponse.from(loginUser);
	}

	/**
	 * A-04 ログアウト。セッションを破棄する。すでにセッションが切れていても、何もせずに終わる。
	 * ゲストなら、先にそのユーザーを消す（要件 U-08。ボードなどは DB の ON DELETE CASCADE で一緒に消える）。
	 */
	@Transactional
	public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		Authentication authentication = securityContextHolderStrategy.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser
				&& loginUser.isGuest()) {
			userRepository.deleteGuestById(loginUser.getId());
		}
		endSession(authentication, httpRequest, httpResponse);
	}

	/**
	 * A-05 ログイン中のユーザー。セッションが残っていても、ユーザーが消えていたら（24時間たって消されたゲストなど）、
	 * セッションを破棄して 401 にする（詳細設計書 3.8.6）。画面はログイン画面へ移る。
	 */
	@Transactional(readOnly = true)
	public UserResponse me(LoginUser loginUser, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		if (!userRepository.existsById(loginUser.getId())) {
			endSession(securityContextHolderStrategy.getContext().getAuthentication(), httpRequest, httpResponse);
			// Spring Security の「ログインしていない」の例外。ApiExceptionHandler が 401 UNAUTHENTICATED にする
			throw new InsufficientAuthenticationException("ユーザーが見つかりません");
		}
		return UserResponse.from(loginUser);
	}

	// セッションを破棄し、CSRF トークンとセッションの Cookie も消す（ログアウトと、ユーザーが消えていたとき）
	private void endSession(Authentication authentication, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, authentication);
		new CsrfLogoutHandler(csrfTokenRepository).logout(httpRequest, httpResponse, authentication);
		new CookieClearingLogoutHandler("SESSION").logout(httpRequest, httpResponse, authentication);
		// 古いトークンは上で消したので、新しいトークンを Cookie で渡す。
		// ログイン画面は API を呼ばないので、これがないと、再読み込みせずにログイン・ゲストログインすると 403 になる
		csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(httpRequest), httpRequest, httpResponse);
	}

	// 作ったばかりのユーザー（登録・ゲスト）で、ログインした状態にする
	private UserResponse startSession(User user, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		LoginUser loginUser = new LoginUser(user);
		loginUser.eraseCredentials(); // セッションにパスワードのハッシュを残さない
		startSession(UsernamePasswordAuthenticationToken.authenticated(loginUser, null, loginUser.getAuthorities()),
				httpRequest, httpResponse);
		return UserResponse.from(loginUser);
	}

	/**
	 * ログインした状態にする（ログイン・登録・ゲストのあとで使う）。
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
