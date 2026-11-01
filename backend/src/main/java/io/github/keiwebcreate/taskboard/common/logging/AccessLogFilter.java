package io.github.keiwebcreate.taskboard.common.logging;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.keiwebcreate.taskboard.auth.LoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * API のアクセスのログ（要件 NO-03、詳細設計書 3.11）。リクエストごとに、終わったときに1行出す。
 * 例：「GET /api/boards/3 200 12ms（ユーザーID 5）」。日時はログの先頭に付く。
 *
 * - URL はパスだけ。クエリ文字列（検索の言葉など）は出さない（NO-04：カードなどの中身を残さない）
 * - ユーザーは ID だけ。ログインしていなければ「未ログイン」
 * - Spring Security の部品の中（ログイン状態を読み込んだすぐ後ろ）に置く（SecurityConfig）。
 *   こうすると、401・403 で断ったリクエストも残り、ログイン状態も読める。
 *   ログインの API では、ログインしたあとのユーザー ID が出る
 */
public class AccessLogFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

	private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
			.getContextHolderStrategy();

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		long start = System.nanoTime();
		boolean failed = true;
		try {
			filterChain.doFilter(request, response);
			failed = false;
		} finally {
			long millis = (System.nanoTime() - start) / 1_000_000;
			// 途中で例外が出たときは、まだステータスが決まっていない（このあと 500 になる）
			int status = failed ? HttpStatus.INTERNAL_SERVER_ERROR.value() : response.getStatus();
			log.info("{} {} {} {}ms（{}）", request.getMethod(), request.getRequestURI(), status, millis, user());
		}
	}

	private String user() {
		Authentication authentication = securityContextHolderStrategy.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
			return "ユーザーID " + loginUser.getId();
		}
		return "未ログイン";
	}

}
