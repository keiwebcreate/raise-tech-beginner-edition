package io.github.keiwebcreate.taskboard.common.error;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 例外をまとめて受け取り、API設計書 4章の Problem Details（JSON）に変換する（詳細設計書 3.6）。
 * Controller に try-catch を書かなくてよいように、エラーの返し方はすべてここに集める。
 * Spring Security の 401・403 も、SecurityConfig からここに回してくる。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

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

	// @Valid の入力チェックに失敗した。項目ごとのメッセージを errors に入れる
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException e) {
		List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
				.map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
				.toList();
		ResponseEntity<ProblemDetail> response = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", MESSAGE_VALIDATION);
		response.getBody().setProperty("errors", errors);
		return response;
	}

	// JSON が壊れている、enum にない値が来た
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ProblemDetail> handleNotReadable(HttpMessageNotReadableException e) {
		return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", MESSAGE_VALIDATION);
	}

	// 存在しない URL
	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", MESSAGE_NOT_FOUND);
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

	// 上のどれでもない例外
	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleOthers(Exception e) {
		// 405（メソッドが違う）など、Spring がステータスを決めている例外は、そのまま返す
		if (e instanceof ErrorResponse errorResponse) {
			return ResponseEntity.status(errorResponse.getStatusCode()).body(errorResponse.getBody());
		}
		// 詳細はログにだけ出し、レスポンスには含めない（要件 5.2）
		log.error("予期しないエラー", e);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", MESSAGE_INTERNAL);
	}

	// API設計書 4.2 の形：{ type, title, status, detail, code }
	private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
		body.setProperty("code", code);
		return ResponseEntity.status(status).body(body);
	}

}
