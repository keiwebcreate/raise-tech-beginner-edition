package io.github.keiwebcreate.taskboard.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import io.github.keiwebcreate.taskboard.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;

/**
 * A-19 退会のテスト（要件 U-11、詳細設計書 3.8.7）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class WithdrawApiTest {

	private static final String PASSWORD = "password123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 正しいパスワードで退会すると_ユーザーとデータが消えてセッションも切れる() throws Exception {
		String email = newEmail();
		Cookie session = signup(email);
		long userId = userId(session);

		withdraw(session, PASSWORD)
				.andExpect(status().isNoContent())
				// セッションの Cookie を消し、新しい CSRF トークンを渡す（ログアウトと同じ）
				.andExpect(cookie().maxAge("SESSION", 0))
				.andExpect(cookie().exists("XSRF-TOKEN"));

		assertThat(count("SELECT count(*) FROM users WHERE id = ?", userId)).isZero();
		// ボード・リストは ON DELETE CASCADE で一緒に消える
		assertThat(count("SELECT count(*) FROM boards WHERE user_id = ?", userId)).isZero();
		mockMvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(status().isUnauthorized());
		// 同じメールアドレスではログインできない
		login(email, PASSWORD).andExpect(status().isUnauthorized());
	}

	@Test
	void 退会すると_ほかのブラウザのセッションも使えなくなる() throws Exception {
		String email = newEmail();
		Cookie first = signup(email);
		Cookie second = login(email, PASSWORD)
				.andExpect(status().isOk())
				.andReturn().getResponse().getCookie("SESSION");
		assertThat(second).isNotNull();

		withdraw(first, PASSWORD).andExpect(status().isNoContent());

		// 消えたユーザーのボードの 404 ではなく、ログインしていない 401（画面はログイン画面へ移る）
		mockMvc.perform(get("/api/board").cookie(second))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		assertThat(count("SELECT count(*) FROM spring_session WHERE principal_name = ?", email)).isZero();
	}

	@Test
	void 退会しても_ほかの人のセッションとデータは残る() throws Exception {
		Cookie other = signup(newEmail());
		Cookie session = signup(newEmail());

		withdraw(session, PASSWORD).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/board").cookie(other)).andExpect(status().isOk());
	}

	@Test
	void パスワードが違うと400で_ユーザーは消えない() throws Exception {
		Cookie session = signup(newEmail());
		long userId = userId(session);

		withdraw(session, "wrong-password")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("password"))
				.andExpect(jsonPath("$.errors[0].message").value("パスワードが正しくありません"));

		assertThat(count("SELECT count(*) FROM users WHERE id = ?", userId)).isOne();
		// ログインしたまま
		mockMvc.perform(get("/api/board").cookie(session)).andExpect(status().isOk());
	}

	@Test
	void パスワードが空でも400で_失敗の回数には数えない() throws Exception {
		String ip = newIpAddress();
		Cookie session = signup(newEmail());

		mockMvc.perform(withdrawRequest(session, "").with(from(ip)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].message").value("パスワードが正しくありません"));

		assertThat(countEvents(ip)).isZero();
	}

	@Test
	void パスワード違いは_ログインの失敗と一緒に数える() throws Exception {
		String ip = newIpAddress();
		String email = newEmail();
		Cookie session = signup(email);
		// ログインの失敗9回 ＋ 退会のパスワード違い1回 ＝ 10回
		for (int i = 0; i < 9; i++) {
			mockMvc.perform(loginRequest(email, "wrong-password").with(from(ip)))
					.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(withdrawRequest(session, "wrong-password").with(from(ip)))
				.andExpect(status().isBadRequest());
		assertThat(countEvents(ip)).isEqualTo(10);

		// 11回目は、正しいパスワードでも 429（退会もログインも）
		mockMvc.perform(withdrawRequest(session, PASSWORD).with(from(ip)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
		mockMvc.perform(loginRequest(email, PASSWORD).with(from(ip)))
				.andExpect(status().isTooManyRequests());
	}

	@Test
	void ゲストは退会できない() throws Exception {
		Cookie session = mockMvc.perform(post("/api/auth/guest").with(csrf()))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getCookie("SESSION");
		long userId = userId(session);

		withdraw(session, PASSWORD)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GUEST_NOT_ALLOWED"))
				.andExpect(jsonPath("$.detail").value("ゲストは退会できません"));

		assertThat(count("SELECT count(*) FROM users WHERE id = ?", userId)).isOne();
	}

	@Test
	void ログインしていないと401() throws Exception {
		mockMvc.perform(post("/api/auth/withdraw").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void CSRFトークンがないと403() throws Exception {
		Cookie session = signup(newEmail());

		mockMvc.perform(post("/api/auth/withdraw").cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	@Test
	void 退会をユーザーIDでログに残し_メールアドレスは出さない(CapturedOutput output) throws Exception {
		String email = newEmail();
		Cookie session = signup(email);
		long userId = userId(session);

		withdraw(session, PASSWORD).andExpect(status().isNoContent());

		assertThat(output.getOut()).contains("退会しました（ユーザーID " + userId + "）");
		assertThat(output.getOut()).doesNotContain(email);
	}

	// ===== 手助けのメソッド =====

	private static String newEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	// テストごとの IP アドレス（RateLimitApiTest と同じ考え方。2001:db8:2:: を使う）
	private static String newIpAddress() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return "2001:db8:2::%x:%x".formatted(random.nextInt(0x10000), random.nextInt(0x10000));
	}

	private static RequestPostProcessor from(String ip) {
		return request -> {
			request.setRemoteAddr(ip);
			return request;
		};
	}

	private Cookie signup(String email) throws Exception {
		Cookie session = mockMvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "email": "%s", "password": "%s" }
						""".formatted(email, PASSWORD)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getCookie("SESSION");
		assertThat(session).isNotNull();
		return session;
	}

	private static MockHttpServletRequestBuilder loginRequest(
			String email, String password) {
		return post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "email": "%s", "password": "%s" }
						""".formatted(email, password));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(loginRequest(email, password));
	}

	private static MockHttpServletRequestBuilder withdrawRequest(
			Cookie session, String password) {
		return post("/api/auth/withdraw").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(password));
	}

	private ResultActions withdraw(Cookie session, String password) throws Exception {
		return mockMvc.perform(withdrawRequest(session, password));
	}

	private static String json(String password) {
		return """
				{ "password": "%s" }
				""".formatted(password);
	}

	private long userId(Cookie session) throws Exception {
		String json = mockMvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(json, "$.id").longValue();
	}

	private long count(String sql, Object param) {
		Long count = jdbcTemplate.queryForObject(sql, Long.class, param);
		return count == null ? 0 : count;
	}

	private long countEvents(String ip) {
		return count("SELECT count(*) FROM rate_limit_events WHERE ip_address = ?", ip);
	}

}
