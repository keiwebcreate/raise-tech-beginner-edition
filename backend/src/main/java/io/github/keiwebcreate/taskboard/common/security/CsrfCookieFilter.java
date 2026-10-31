package io.github.keiwebcreate.taskboard.common.security;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * すべてのレスポンスで CSRF トークンを読み込み、Cookie XSRF-TOKEN を必ず返すようにする（詳細設計書 3.8.4）。
 * Spring Security はトークンを「使うときに初めて作る」ので、GET だけのときは Cookie が返らない。
 * React は最初の POST（ログインなど）の前に Cookie を持っている必要があるため、ここで読み込ませる。
 */
class CsrfCookieFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (csrfToken != null) {
			// 値を読むと、トークンが作られて Cookie に書き込まれる
			csrfToken.getToken();
		}
		filterChain.doFilter(request, response);
	}

}
