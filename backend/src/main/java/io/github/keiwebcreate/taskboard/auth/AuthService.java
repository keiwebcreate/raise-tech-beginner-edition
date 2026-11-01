package io.github.keiwebcreate.taskboard.auth;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.keiwebcreate.taskboard.auth.dto.EmailChangeRequest;
import io.github.keiwebcreate.taskboard.auth.dto.LoginRequest;
import io.github.keiwebcreate.taskboard.auth.dto.PasswordChangeRequest;
import io.github.keiwebcreate.taskboard.auth.dto.SignupRequest;
import io.github.keiwebcreate.taskboard.auth.dto.UserResponse;
import io.github.keiwebcreate.taskboard.auth.dto.WithdrawRequest;
import io.github.keiwebcreate.taskboard.board.BoardService;
import io.github.keiwebcreate.taskboard.common.error.EmailDuplicatedException;
import io.github.keiwebcreate.taskboard.common.error.FieldValidationException;
import io.github.keiwebcreate.taskboard.common.error.GuestNotAllowedException;
import io.github.keiwebcreate.taskboard.common.error.LoginFailedException;
import io.github.keiwebcreate.taskboard.common.error.RateLimitedException;
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

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	// 登録したときに作るボードの名前（要件 U-02、テーブル設計書 9.1）。3つのリストは BoardService が入れる
	private static final String INITIAL_BOARD_NAME = "マイボード";

	private final UserRepository userRepository;
	private final BoardService boardService;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final CsrfTokenRepository csrfTokenRepository;
	private final GuestSampleData guestSampleData;
	private final RateLimiter rateLimiter;
	private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
	private final TransactionTemplate transactionTemplate;
	private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
			.getContextHolderStrategy();

	public AuthService(UserRepository userRepository, BoardService boardService, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository,
			CsrfTokenRepository csrfTokenRepository, GuestSampleData guestSampleData, RateLimiter rateLimiter,
			FindByIndexNameSessionRepository<? extends Session> sessionRepository,
			TransactionTemplate transactionTemplate) {
		this.userRepository = userRepository;
		this.boardService = boardService;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.csrfTokenRepository = csrfTokenRepository;
		this.guestSampleData = guestSampleData;
		this.rateLimiter = rateLimiter;
		this.sessionRepository = sessionRepository;
		this.transactionTemplate = transactionTemplate;
	}

	/**
	 * A-01 ユーザー登録。ボードと3つのリストも作り、そのままログインした状態にする。
	 *
	 * 回数制限（要件 U-10、詳細設計書 3.8.8）：登録できたときと、登録済みで断ったときを数える。
	 * 登録済みも数えるのは、409 の返事で「このメールアドレスは使われているか」を次々に調べられないようにするため。
	 * 制限中は、登録済みかどうかを確かめずに 429 にする。
	 *
	 * トランザクションは TransactionTemplate で書く（退会 withdraw と同じ）。登録済みのときも記録は確定させたいので、
	 * 中では例外を投げずに null を返し、外で 409 にする（中で例外を投げると、記録も取り消される）。
	 *
	 * @throws RateLimitedException     同じ IP アドレスから、1時間に10回を超えて登録しようとしたとき
	 * @throws EmailDuplicatedException 登録済みのメールアドレスのとき
	 */
	public UserResponse signup(SignupRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		String email = normalizeEmail(request.email());
		String ipAddress = httpRequest.getRemoteAddr();

		User user;
		try {
			user = transactionTemplate.execute(status -> {
				// 数えてから記録する。鍵はトランザクションが終わるまでかかっているので、同時に送られても上限を超えない
				rateLimiter.check(RateLimitKind.REGISTRATION, ipAddress);
				rateLimiter.record(RateLimitKind.REGISTRATION, ipAddress);
				if (userRepository.existsByEmail(email)) {
					return null;
				}
				User saved = userRepository.save(new User(email, passwordEncoder.encode(request.password()), false));
				boardService.createWithInitialColumns(saved, INITIAL_BOARD_NAME);
				return saved;
			});
		} catch (DataIntegrityViolationException e) {
			// 上の確認と保存の間に、同じメールアドレスで登録された（ほぼ同時の登録）。
			// DB の一意制約（uk_users_email）が2人目を止めるので、確認で見つかったときと同じ 409 にする。
			// このときは記録も取り消される（めったに起きず、同じ IP アドレスからは鍵で1つずつ処理されるので起きない）
			throw new EmailDuplicatedException();
		}
		if (user == null) {
			throw new EmailDuplicatedException();
		}
		// 登録を残す（要件 5章のログ）。ユーザーは ID で記録し、メールアドレスは出さない
		log.info("ユーザー登録しました（ユーザーID {}）", user.getId());
		return startSession(user, httpRequest, httpResponse);
	}

	/**
	 * A-03 ゲストログイン（要件 U-06）。押すたびに新しいゲストを作り、サンプルデータの入ったボードを用意して、ログインした状態にする。
	 * メールアドレスとパスワードはランダム。パスワードは誰も知らないので、ゲストのメールアドレスではログインできない（A-02）。
	 *
	 * トランザクションは TransactionTemplate で「回数制限・ゲストの作成」だけにかけ、確定したあとでログを出す
	 * （確定の前に出すと、確定に失敗したときにログだけが残るため。品質チェック #53）。
	 *
	 * @throws RateLimitedException 同じ IP アドレスから、1時間に10回を超えて作ろうとしたとき（要件 U-10）
	 */
	public UserResponse guest(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		String ipAddress = httpRequest.getRemoteAddr();
		User user = Objects.requireNonNull(transactionTemplate.execute(status -> {
			// 回数制限：数えてから記録する。どちらも同じトランザクションの中なので、ゲストの作成が失敗したら記録も取り消される
			rateLimiter.check(RateLimitKind.GUEST_CREATION, ipAddress);
			rateLimiter.record(RateLimitKind.GUEST_CREATION, ipAddress);

			// UUID は乱数で作る128ビットの値なので、重なったり当てられたりしない
			String email = "guest-" + UUID.randomUUID() + "@guest.local";
			String password = UUID.randomUUID().toString();
			User saved = userRepository.save(new User(email, passwordEncoder.encode(password), true));
			guestSampleData.create(saved);
			return saved;
		}));
		// ゲストのログインを残す（要件 5章のログ。ユーザー ID だけ）
		log.info("ゲストでログインしました（ユーザーID {}）", user.getId());
		return startSession(user, httpRequest, httpResponse);
	}

	/**
	 * A-02 ログイン。
	 *
	 * 先に回数制限を確かめる。制限中は、パスワードを照合せずに 429 にする（正しいパスワードでも）。
	 * 失敗したら、その IP アドレスの失敗を1回記録する。成功しても、記録は消さない（詳細設計書 3.8.8）。
	 *
	 * トランザクションは TransactionTemplate で「回数制限・照合」だけにかける（登録・退会と同じ）。
	 * 失敗の記録は確定させたいので、中では例外を投げずに null を返し、確定したあとでログを出して 401 にする
	 * （中で例外を投げると、失敗の記録も取り消される）。
	 *
	 * @throws RateLimitedException 同じ IP アドレスから、15分に10回を超えて失敗したとき（要件 U-10）
	 * @throws LoginFailedException メールアドレスかパスワードが違うとき（どちらが違うかは教えない）
	 */
	public UserResponse login(LoginRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		String ipAddress = httpRequest.getRemoteAddr();
		String email = normalizeEmail(request.email());
		String password = nullToEmpty(request.password());

		Authentication authentication = transactionTemplate.execute(status -> {
			rateLimiter.check(RateLimitKind.LOGIN_FAILURE, ipAddress);
			try {
				// LoginUserDetailsService でユーザーを探し、BCrypt でパスワードを照合する
				return authenticationManager
						.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
			} catch (AuthenticationException e) {
				rateLimiter.record(RateLimitKind.LOGIN_FAILURE, ipAddress);
				return null;
			}
		});
		// 照合に成功すると、中身は LoginUserDetailsService が返した LoginUser になる（型の上では null もありうるので確かめる）
		if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
			// ログインの失敗を残す（要件 5章のログ）。まだ誰かわからず、メールアドレスは出さないので、IP アドレスを出す
			log.info("ログインに失敗しました（IPアドレス {}）", ipAddress);
			throw new LoginFailedException();
		}
		startSession(authentication, httpRequest, httpResponse);
		// ログインの成功を残す（要件 5章のログ。ユーザー ID だけ）
		log.info("ログインしました（ユーザーID {}）", loginUser.getId());
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
	 * A-19 退会（要件 U-11、詳細設計書 3.8.7）。パスワードで本人か確かめてから、ユーザーを削除する
	 * （ボード・リスト・カード・ラベルは DB の ON DELETE CASCADE で一緒に消える）。
	 * その人のセッションは、ほかのブラウザ・端末のものも全部消す（消えたアカウントのログイン状態を残さない）。
	 * パスワード違いは、ログインの失敗と一緒に数える（要件 U-10）。ここで何度でも試せないようにするため。
	 *
	 * トランザクションは @Transactional ではなく、TransactionTemplate で「回数制限・照合・削除」の部分だけにかける。
	 * セッションを消す処理は、Spring Session が別のトランザクション（REQUIRES_NEW）で動かすので、
	 * 削除を確定して DB の接続を返してから呼ぶ。中で呼ぶと、削除が取り消されてもセッションだけ消えてしまい、
	 * 1つの退会が接続を2本使うので、同時に退会が重なると接続が足りなくなる（品質チェック #43）。
	 *
	 * @throws GuestNotAllowedException ゲストのとき（ゲストはログアウトでデータが消えるので、退会はない）
	 * @throws RateLimitedException     同じ IP アドレスから、15分に10回を超えて失敗したとき
	 * @throws FieldValidationException パスワードが空・違うとき
	 */
	public void withdraw(LoginUser loginUser, WithdrawRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		if (loginUser.isGuest()) {
			throw new GuestNotAllowedException("ゲストは退会できません");
		}
		String ipAddress = httpRequest.getRemoteAddr();
		String password = nullToEmpty(request.password());

		// パスワード違いでも、失敗の記録は確定させたい。なので中では例外を投げず、消したかどうか（true / false）を返す
		Boolean deleted = transactionTemplate
				.execute(status -> deleteIfPasswordMatches(loginUser.getId(), password, ipAddress));
		if (!Boolean.TRUE.equals(deleted)) {
			throw new FieldValidationException("password", "パスワードが正しくありません");
		}

		// ここからは削除が確定したあと。Spring Session は、ログイン状態の持ち主の名前
		// （LoginUser.getUsername() のメールアドレス）でセッションを探せる
		sessionRepository.findByPrincipalName(loginUser.getEmail()).keySet().forEach(sessionRepository::deleteById);
		endSession(securityContextHolderStrategy.getContext().getAuthentication(), httpRequest, httpResponse);
		// 退会を残す（要件 5章のログ）。ユーザーは ID で記録し、メールアドレスは出さない
		log.info("退会しました（ユーザーID {}）", loginUser.getId());
	}

	// 退会のトランザクションの中身。パスワードが合えばユーザーを削除して true、違えば false
	private boolean deleteIfPasswordMatches(Long userId, String password, String ipAddress) {
		User user = findIfPasswordMatches(userId, password, ipAddress);
		if (user == null) {
			return false;
		}
		userRepository.delete(user);
		return true;
	}

	/**
	 * A-20 メールアドレスの変更（詳細設計書 3.8.9）。今のパスワードで本人か確かめてから変える。
	 * ほかのブラウザ・端末のセッションは消し、今のセッションは新しいメールアドレスでログインしたままにする。
	 * トランザクションの分け方とパスワード違いの数え方は、退会（withdraw）と同じ。
	 *
	 * @throws GuestNotAllowedException ゲストのとき（ゲストのメールアドレスはダミー）
	 * @throws RateLimitedException     同じ IP アドレスから、15分に10回を超えて失敗したとき
	 * @throws FieldValidationException 今のパスワードが空・違うとき
	 * @throws EmailDuplicatedException 新しいメールアドレスが、ほかの人に登録済みのとき
	 */
	public UserResponse changeEmail(LoginUser loginUser, EmailChangeRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		if (loginUser.isGuest()) {
			throw new GuestNotAllowedException("ゲストはメールアドレスを変更できません");
		}
		String ipAddress = httpRequest.getRemoteAddr();
		String password = nullToEmpty(request.currentPassword());
		String newEmail = normalizeEmail(request.email());

		User user;
		try {
			user = transactionTemplate.execute(status -> {
				User found = findIfPasswordMatches(loginUser.getId(), password, ipAddress);
				if (found == null) {
					return null;
				}
				// 重複の確認は、パスワードが合ってから（合う前に答えると、登録済みのアドレスを調べられてしまう）。
				// 今と同じアドレスは、自分なので重複にしない
				if (!found.getEmail().equals(newEmail) && userRepository.existsByEmail(newEmail)) {
					throw new EmailDuplicatedException();
				}
				found.changeEmail(newEmail);
				// ここで DB に書き込む。ほぼ同時の登録・変更とぶつかったら、下の catch で 409 にする
				return userRepository.saveAndFlush(found);
			});
		} catch (DataIntegrityViolationException e) {
			// 上の確認と書き込みの間に、同じメールアドレスで登録された（登録の signup と同じ）
			throw new EmailDuplicatedException();
		}
		if (user == null) {
			throw new FieldValidationException("currentPassword", "パスワードが正しくありません");
		}

		// ここからは変更が確定したあと。ほかの端末のセッションは、変える前のメールアドレスで探す
		endOtherSessions(loginUser.getEmail(), httpRequest);
		UserResponse response = startSession(user, httpRequest, httpResponse);
		issueCsrfToken(httpRequest);
		log.info("メールアドレスを変更しました（ユーザーID {}）", user.getId());
		return response;
	}

	/**
	 * A-21 パスワードの変更（詳細設計書 3.8.9）。今のパスワードで本人か確かめてから変える。
	 * ほかのブラウザ・端末のセッションは消し（パスワードが知られたかもしれないと思って変える人が多いため）、
	 * 今のセッションはログインしたまま、セッション ID だけ新しくする。
	 *
	 * @throws GuestNotAllowedException ゲストのとき（ゲストのパスワードは誰も知らない）
	 * @throws RateLimitedException     同じ IP アドレスから、15分に10回を超えて失敗したとき
	 * @throws FieldValidationException 今のパスワードが空・違うとき
	 */
	public void changePassword(LoginUser loginUser, PasswordChangeRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		if (loginUser.isGuest()) {
			throw new GuestNotAllowedException("ゲストはパスワードを変更できません");
		}
		String ipAddress = httpRequest.getRemoteAddr();
		String password = nullToEmpty(request.currentPassword());

		Boolean changed = transactionTemplate.execute(status -> {
			User found = findIfPasswordMatches(loginUser.getId(), password, ipAddress);
			if (found == null) {
				return false;
			}
			found.changePasswordHash(passwordEncoder.encode(request.newPassword()));
			return true;
		});
		if (!Boolean.TRUE.equals(changed)) {
			throw new FieldValidationException("currentPassword", "パスワードが正しくありません");
		}

		endOtherSessions(loginUser.getEmail(), httpRequest);
		startSession(UsernamePasswordAuthenticationToken.authenticated(loginUser, null, loginUser.getAuthorities()),
				httpRequest, httpResponse);
		issueCsrfToken(httpRequest);
		log.info("パスワードを変更しました（ユーザーID {}）", loginUser.getId());
	}

	/**
	 * 今のパスワードの照合（退会・メールアドレスの変更・パスワードの変更で使う）。トランザクションの中で呼ぶ。
	 * 先に回数制限を確かめ（ログインの失敗と同じ LOGIN_FAILURE。制限中は 429）、合えばユーザーを返す。
	 * 違えば失敗を1回記録して null を返す（空のときは、パスワードを試したことにならないので数えない）。
	 * 例外ではなく null を返すのは、失敗の記録を取り消さずに確定させるため。
	 */
	private User findIfPasswordMatches(Long userId, String password, String ipAddress) {
		rateLimiter.check(RateLimitKind.LOGIN_FAILURE, ipAddress);

		// ログイン中の本人の行。ID はセッションに入っている自分のものなので、持ち主の確認は要らない。
		// 見つからない（すでに消えている）ときは、/me と同じく「ログインしていない」（401）にする
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new InsufficientAuthenticationException("ユーザーが見つかりません"));
		if (password.isEmpty() || !passwordEncoder.matches(password, user.getPasswordHash())) {
			if (!password.isEmpty()) {
				rateLimiter.record(RateLimitKind.LOGIN_FAILURE, ipAddress);
			}
			return null;
		}
		return user;
	}

	// 今のセッション以外の、その人のセッションを全部消す（メールアドレス・パスワードの変更のあと）。
	// 持ち主の名前は、変える前のメールアドレス（LoginUser.getUsername()）
	private void endOtherSessions(String principalName, HttpServletRequest httpRequest) {
		String currentSessionId = httpRequest.getSession().getId();
		sessionRepository.findByPrincipalName(principalName).keySet().stream()
				.filter(id -> !id.equals(currentSessionId))
				.forEach(sessionRepository::deleteById);
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

	/**
	 * startSession で作り直した CSRF トークンを、この返事の Cookie ですぐ渡す（メールアドレス・パスワードの変更のあと）。
	 * ふつうは次のリクエストで渡す（CsrfCookieFilter）。ログインのあとは画面がボードを GET するので、それで間に合う。
	 * 変更のあとは画面がそのまま残り、次がカードの保存などの PUT・POST になりうるので、ここで渡さないと 403 になる。
	 * トークンの値を読むと、作られて Cookie に書き込まれる（CsrfCookieFilter と同じ）。
	 */
	private static void issueCsrfToken(HttpServletRequest httpRequest) {
		if (httpRequest.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken csrfToken) {
			csrfToken.getToken();
		}
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	// 前後の空白を取り除き、小文字にそろえる（要件 3章：Taro@example.com と taro@example.com は同じ人）
	private static String normalizeEmail(String email) {
		return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
	}

}
