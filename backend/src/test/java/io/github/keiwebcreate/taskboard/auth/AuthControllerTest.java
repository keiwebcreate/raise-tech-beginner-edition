package io.github.keiwebcreate.taskboard.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import io.github.keiwebcreate.taskboard.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;

/**
 * ログイン関係の API とボード取得の API を、実際に HTTP で呼ぶ形で確かめる（詳細設計書 3.10）。
 * - @SpringBootTest：アプリ全体を起動する（DB は Testcontainers の PostgreSQL）
 * - MockMvc：サーバーを本当に立てずに、API を呼んだのと同じ処理を動かす
 * - with(csrf())：正しい CSRF トークンを付ける（React がヘッダー X-XSRF-TOKEN を付けるのにあたる）
 * テストごとにメールアドレスを変えて、ほかのテストのデータとぶつからないようにしている。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerTest {

	private static final String PASSWORD = "password123";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 登録するとログインした状態になり_自分のボードを取得できる() throws Exception {
		String email = newEmail();
		Cookie session = signup(email);

		mockMvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.isGuest").value(false));

		// 登録したときに、ボードと3つのリストができている（要件 U-02）
		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("マイボード"))
				.andExpect(jsonPath("$.columns.length()").value(3))
				.andExpect(jsonPath("$.columns[0].name").value("未着手"))
				.andExpect(jsonPath("$.columns[1].name").value("作業中"))
				.andExpect(jsonPath("$.columns[2].name").value("完了"))
				.andExpect(jsonPath("$.labels.length()").value(0));
	}

	@Test
	void メールアドレスは小文字にそろえて登録される() throws Exception {
		String email = newEmail();

		mockMvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(email.toUpperCase(), PASSWORD)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void 登録済みのメールアドレスでは登録できない() throws Exception {
		String email = newEmail();
		signup(email);

		mockMvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(email, PASSWORD)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"))
				.andExpect(jsonPath("$.detail").value("このメールアドレスは登録済みです"));
	}

	@Test
	void 登録の入力が正しくないと400で項目ごとのメッセージを返す() throws Exception {
		mockMvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json("メールではない", "短い")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[?(@.field == 'email')].message")
						.value("メールアドレスの形式が正しくありません"))
				.andExpect(jsonPath("$.errors[?(@.field == 'password')].message")
						.value("パスワードは8〜64文字の半角英数字・記号で入力してください"));
	}

	@Test
	void 正しいメールアドレスとパスワードでログインできる() throws Exception {
		String email = newEmail();
		signup(email);

		MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(email, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andReturn();

		mockMvc.perform(get("/api/board").cookie(sessionCookie(result)))
				.andExpect(status().isOk());
	}

	@Test
	void パスワードが違うとログインできない() throws Exception {
		String email = newEmail();
		signup(email);

		mockMvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(email, "wrong-password")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
				.andExpect(jsonPath("$.detail").value("メールアドレスまたはパスワードが正しくありません"));
	}

	@Test
	void 登録されていないメールアドレスでも同じエラーになる() throws Exception {
		mockMvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(newEmail(), PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
	}

	@Test
	void ログインしていないとボードを取得できない() throws Exception {
		mockMvc.perform(get("/api/board"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
				.andExpect(jsonPath("$.detail").value("ログインしてください"));
	}

	@Test
	void CSRFトークンがないと403になる() throws Exception {
		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(newEmail(), PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	// GET で CSRF トークンの Cookie が返ることは CsrfCookieTest で確かめる
	// （with(csrf()) を使ったあとのテストでは Cookie が返らなくなるため、別のクラスにした）

	@Test
	void ログアウトするとボードを取得できなくなる() throws Exception {
		Cookie session = signup(newEmail());

		mockMvc.perform(post("/api/auth/logout").with(csrf()).cookie(session))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void ログインしていなくてもログアウトは204を返す() throws Exception {
		mockMvc.perform(post("/api/auth/logout").with(csrf()))
				.andExpect(status().isNoContent());
	}

	// 登録して、ログイン状態のセッションの Cookie を返す
	private Cookie signup(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(email, PASSWORD)))
				.andExpect(status().isCreated())
				.andReturn();
		return sessionCookie(result);
	}

	private static Cookie sessionCookie(MvcResult result) {
		Cookie session = result.getResponse().getCookie("SESSION");
		assertThat(session).as("セッションの Cookie が返っている").isNotNull();
		return session;
	}

	private static String newEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	private static String json(String email, String password) {
		return """
				{ "email": "%s", "password": "%s" }
				""".formatted(email, password);
	}

}
