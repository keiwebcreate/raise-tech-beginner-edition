package io.github.keiwebcreate.taskboard.common.error;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 例外をまとめて受け取り、API設計書 4章の Problem Details（JSON）に変換する（詳細設計書 3.6）。
 * Controller に try-catch を書かなくてよいように、エラーの返し方はすべてここに集める。
 * Spring Security の 401・403 も、SecurityConfig からここに回してくる。
 *
 * Spring MVC が投げる例外（入力チェックの失敗、JSON が読めない、URL の ID が数字でない、存在しない URL、
 * メソッドの違いなど）は、Spring 標準の ResponseEntityExceptionHandler が正しいステータスを決める。
 * その最後に必ず呼ばれる handleExceptionInternal で、このアプリの code と日本語のメッセージを付ける。
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	// 画面にそのまま出すメッセージ（画面設計書 7章）
	private static final String MESSAGE_VALIDATION = "入力内容に誤りがあります";
	private static final String MESSAGE_UNAUTHENTICATED = "ログインしてください";
	private static final String MESSAGE_CSRF = "通信に失敗しました。画面を再読み込みしてください";
	private static final String MESSAGE_NOT_FOUND = "対象のデータが見つかりません。画面を再読み込みしてください";
	private static final String MESSAGE_INTERNAL = "通信に失敗しました。時間をおいて再度お試しください";

	@ExceptionHandler(NotFoundException.class)
	ResponseEntity<ProblemDetail> handleNotFound(NotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", MESSAGE_NOT_FOUND);
	}

	@ExceptionHandler(EmailDuplicatedException.class)
	ResponseEntity<ProblemDetail> handleEmailDuplicated(EmailDuplicatedException e) {
		return problem(HttpStatus.CONFLICT, "EMAIL_DUPLICATED", e.getMessage());
	}

	@ExceptionHandler(LimitExceededException.class)
	ResponseEntity<ProblemDetail> handleLimitExceeded(LimitExceededException e) {
		return problem(HttpStatus.CONFLICT, "LIMIT_EXCEEDED", e.getMessage());
	}

	@ExceptionHandler(LoginFailedException.class)
	ResponseEntity<ProblemDetail> handleLoginFailed(LoginFailedException e) {
		return problem(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", e.getMessage());
	}

	// ログインしていない（Spring Security の AuthenticationEntryPoint から回ってくる）
	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ProblemDetail> handleUnauthenticated(AuthenticationException e) {
		return problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", MESSAGE_UNAUTHENTICATED);
	}

	// CSRF トークンがない・合わない（Spring Security の AccessDeniedHandler から回ってくる）
	@ExceptionHandler(CsrfException.class)
	ResponseEntity<ProblemDetail> handleCsrf(CsrfException e) {
		return problem(HttpStatus.FORBIDDEN, "CSRF_INVALID", MESSAGE_CSRF);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException e) {
		return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", HttpStatus.FORBIDDEN.getReasonPhrase());
	}

	// 上のどれでもなく、Spring MVC の例外でもないもの（プログラムの不具合など）
	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleOthers(Exception e) {
		// 詳細はログにだけ出し、レスポンスには含めない（要件 5.2）
		log.error("予期しないエラー", e);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", MESSAGE_INTERNAL);
	}

	/**
	 * Spring MVC の例外を Problem Details にする。ステータスは Spring が決めたものをそのまま使う。
	 * - 400：VALIDATION_ERROR。@Valid の失敗なら、項目ごとのメッセージを errors に入れる
	 * - 404：NOT_FOUND（存在しない URL）
	 * - 500 台：INTERNAL_ERROR
	 * - それ以外（405 メソッドの違い、415 Content-Type の違いなど）：ステータスの名前を code にする。画面からは起きない
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ProblemDetail problem;
		if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
			problem = problemDetail(statusCode, "VALIDATION_ERROR", MESSAGE_VALIDATION);
			if (ex instanceof MethodArgumentNotValidException notValid) {
				problem.setProperty("errors", fieldErrors(notValid));
			}
		} else if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
			problem = problemDetail(statusCode, "NOT_FOUND", MESSAGE_NOT_FOUND);
		} else if (statusCode.is5xxServerError()) {
			log.error("予期しないエラー", ex);
			problem = problemDetail(statusCode, "INTERNAL_ERROR", MESSAGE_INTERNAL);
		} else {
			HttpStatus status = HttpStatus.resolve(statusCode.value());
			String code = status == null ? "ERROR" : status.name();
			problem = problemDetail(statusCode, code, MESSAGE_INTERNAL);
		}
		// Allow ヘッダー（405 のとき）など、Spring が用意したヘッダーはそのまま返す
		return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
	}

	// @Valid の失敗を [{ field, message }] の形にする
	private static List<Map<String, String>> fieldErrors(MethodArgumentNotValidException e) {
		return e.getBindingResult().getFieldErrors().stream()
				.map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
				.toList();
	}

	// API設計書 4.2 の形：{ type, title, status, detail, code }
	private static ProblemDetail problemDetail(HttpStatusCode status, String code, String detail) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
		body.setProperty("code", code);
		return body;
	}

	private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
		return ResponseEntity.status(status).body(problemDetail(status, code, detail));
	}

}
