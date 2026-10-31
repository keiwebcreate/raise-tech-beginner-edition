package io.github.keiwebcreate.taskboard.common.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Spring Security の設定（API設計書 2.2・2.3、詳細設計書 3.8.3・3.8.4）。
 * - 登録・ログイン・ログアウト以外の API はログインが必要
 * - CSRF：Cookie XSRF-TOKEN でトークンを渡し、ヘッダー X-XSRF-TOKEN と照合する（SPA 向けの設定）
 * - 401・403 は ApiExceptionHandler に回して、ほかのエラーと同じ Problem Details にする
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			SecurityContextRepository securityContextRepository,
			CsrfTokenRepository csrfTokenRepository,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
		http
				.authorizeHttpRequests(authorize -> authorize
						// ログインしていなくても呼べる API。ログアウトはセッションが切れていても 204 を返すため（A-04）
						.requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login", "/api/auth/logout")
						.permitAll()
						.anyRequest().authenticated())
				.csrf(csrf -> csrf
						// spa()：Cookie から読める XSRF-TOKEN と、ヘッダーでの照合を設定する
						.spa()
						// AuthService でログイン後にトークンを作り直すので、同じものを使う
						.csrfTokenRepository(csrfTokenRepository))
				.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
				// ログイン状態はセッションに保存する。AuthService でも同じものを使う
				.securityContext(context -> context.securityContextRepository(securityContextRepository))
				// ログイン・ログアウトの API は自分で作るので、Spring Security が用意する画面や URL は使わない
				.logout(logout -> logout.disable())
				.requestCache(cache -> cache.disable())
				.exceptionHandling(exception -> exception
						// 未ログイン → 401、CSRF 違反など → 403 を、ApiExceptionHandler で JSON にする
						.authenticationEntryPoint((request, response, e) -> exceptionResolver
								.resolveException(request, response, null, e))
						.accessDeniedHandler((request, response, e) -> exceptionResolver
								.resolveException(request, response, null, e)));
		return http.build();
	}

	// パスワードは BCrypt でハッシュ化して保存・照合する（要件 5.2）
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// ログインの照合をする部品。LoginUserDetailsService でユーザーを探し、パスワードを BCrypt で比べる
	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	// React が読めるように HttpOnly を付けない（API設計書 2.3）
	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		return CookieCsrfTokenRepository.withHttpOnlyFalse();
	}

}
