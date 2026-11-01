package io.github.keiwebcreate.taskboard.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import io.github.keiwebcreate.taskboard.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;

/**
 * CSRF トークンの Cookie を、ブラウザと同じ形（Cookie の値をヘッダー X-XSRF-TOKEN に入れる）で確かめる（API設計書 2.3）。
 *
 * ほかのテストで使っている with(csrf()) は、CSRF の確認をする部品（アプリ全体で1つ）のトークンの保存先を、
 * テスト用のもの（Cookie を使わない）に差し替えたままにする。同じアプリを使い回すと、そのあとのテストでは
 * Cookie が返らなくなる。そこで、このクラスは新しく起動したアプリで動かし（@DirtiesContext）、with(csrf()) を使わない。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = ClassMode.BEFORE_CLASS)
class CsrfCookieTest {

	private static final String HEADER = "X-XSRF-TOKEN";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void GETのレスポンスでもCSRFトークンのCookieが返る() throws Exception {
		// React は最初のログインの前に、この Cookie を受け取っておく必要がある
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(cookie().exists("XSRF-TOKEN"))
				.andExpect(cookie().httpOnly("XSRF-TOKEN", false));
	}

	@Test
	void ログアウトのあと_画面を再読み込みせずにゲストログインし直せる() throws Exception {
		// 画面を開いたとき（GET）に受け取ったトークンで、ゲストログインする
		Cookie token = lastXsrfCookie(mockMvc.perform(get("/api/auth/me")).andReturn());
		MvcResult guest = mockMvc.perform(post("/api/auth/guest").cookie(token).header(HEADER, token.getValue()))
				.andExpect(status().isCreated())
				.andReturn();
		Cookie session = guest.getResponse().getCookie("SESSION");
		// ログインすると古いトークンは消え、新しいトークンは次のリクエストで渡される（API設計書 2.3）。
		// 画面はログインのあとにボードを GET するので、そのときに受け取る
		Cookie loggedInToken = lastXsrfCookie(mockMvc.perform(get("/api/board").cookie(session)).andReturn());

		MvcResult logout = mockMvc.perform(post("/api/auth/logout").cookie(session, loggedInToken)
				.header(HEADER, loggedInToken.getValue()))
				.andExpect(status().isNoContent())
				.andReturn();
		// 古いトークンを消す Cookie と、新しいトークンの Cookie の両方が、この順で返る。ブラウザはあとのほうを使う
		Cookie newToken = lastXsrfCookie(logout);
		assertThat(newToken.getMaxAge()).isNotZero();

		// ログイン画面は API を呼ばないので、ログアウトの返事でもらったトークンだけで送る。これがないと 403 になっていた
		mockMvc.perform(post("/api/auth/guest").cookie(newToken).header(HEADER, newToken.getValue()))
				.andExpect(status().isCreated());
	}

	@Test
	void パスワードを変えたあと_画面を再読み込みせずに次の変更を送れる() throws Exception {
		Cookie session = signupWithCookieToken("csrf-" + UUID.randomUUID() + "@example.com");
		Cookie token = lastXsrfCookie(mockMvc.perform(get("/api/board").cookie(session)).andReturn());

		MvcResult change = mockMvc.perform(put("/api/auth/password").cookie(session, token)
				.header(HEADER, token.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "currentPassword": "password123", "newPassword": "newpass456" }
						"""))
				.andExpect(status().isNoContent())
				.andReturn();
		// 変更の返事で、新しいセッションと新しいトークンをすぐ受け取る（ログインのときと違い、次の GET を待たない）
		Cookie renewed = change.getResponse().getCookie("SESSION");
		Cookie newToken = lastXsrfCookie(change);
		assertThat(newToken.getMaxAge()).isNotZero();
		assertThat(newToken.getValue()).isNotEqualTo(token.getValue());

		// 画面はボードのまま、次にボード名の変更（PATCH）を送る
		mockMvc.perform(patch("/api/board").cookie(renewed, newToken)
				.header(HEADER, newToken.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "新しい名前" }
						"""))
				.andExpect(status().isOk());
	}

	@Test
	void メールアドレスを変えたあと_画面を再読み込みせずに次の変更を送れる() throws Exception {
		Cookie session = signupWithCookieToken("csrf-" + UUID.randomUUID() + "@example.com");
		Cookie token = lastXsrfCookie(mockMvc.perform(get("/api/board").cookie(session)).andReturn());

		MvcResult change = mockMvc.perform(put("/api/auth/email").cookie(session, token)
				.header(HEADER, token.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "email": "csrf-%s@example.com", "currentPassword": "password123" }
						""".formatted(UUID.randomUUID())))
				.andExpect(status().isOk())
				.andReturn();
		Cookie renewed = change.getResponse().getCookie("SESSION");
		Cookie newToken = lastXsrfCookie(change);
		assertThat(newToken.getMaxAge()).isNotZero();

		mockMvc.perform(patch("/api/board").cookie(renewed, newToken)
				.header(HEADER, newToken.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "新しい名前" }
						"""))
				.andExpect(status().isOk());
	}

	// 画面と同じく、GET で受け取ったトークンで登録する。返すのはセッションの Cookie
	private Cookie signupWithCookieToken(String email) throws Exception {
		Cookie token = lastXsrfCookie(mockMvc.perform(get("/api/auth/me")).andReturn());
		Cookie session = mockMvc.perform(post("/api/auth/signup").cookie(token).header(HEADER, token.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "email": "%s", "password": "password123" }
						""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getCookie("SESSION");
		assertThat(session).isNotNull();
		return session;
	}

	// レスポンスの XSRF-TOKEN の Cookie のうち、最後のもの（ブラウザが最後に受け取る値）
	private static Cookie lastXsrfCookie(MvcResult result) {
		Cookie cookie = Arrays.stream(result.getResponse().getCookies())
				.filter(c -> c.getName().equals("XSRF-TOKEN"))
				.reduce((first, second) -> second)
				.orElseThrow();
		assertThat(cookie.getValue()).isNotEmpty();
		return cookie;
	}

}
