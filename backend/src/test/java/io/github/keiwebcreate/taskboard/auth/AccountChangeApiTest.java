package io.github.keiwebcreate.taskboard.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import io.github.keiwebcreate.taskboard.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;

/**
 * A-20 メールアドレスの変更と A-21 パスワードの変更のテスト（要件 U-12・U-13、詳細設計書 3.8.9）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AccountChangeApiTest {

	private static final String PASSWORD = "password123";
	private static final String NEW_PASSWORD = "newpass456!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// ===== A-20 メールアドレスの変更 =====

	@Test
	void メールアドレスを変えると_新しいアドレスでログインでき_古いアドレスではできない() throws Exception {
		String oldEmail = newEmail();
		String newEmail = newEmail();
		Cookie session = signup(oldEmail);
		long userId = userId(session);

		MvcResult result = changeEmail(session, newEmail, PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(userId))
				.andExpect(jsonPath("$.email").value(newEmail))
				.andExpect(jsonPath("$.isGuest").value(false))
				.andReturn();

		// 今の画面はログインしたまま。セッション ID は新しくなる（古い ID は使えない）
		Cookie renewed = result.getResponse().getCookie("SESSION");
		assertThat(renewed).isNotNull();
		assertThat(renewed.getValue()).isNotEqualTo(session.getValue());
		mockMvc.perform(get("/api/auth/me").cookie(renewed))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(newEmail));
		mockMvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(status().isUnauthorized());

		login(newEmail, PASSWORD).andExpect(status().isOk());
		login(oldEmail, PASSWORD).andExpect(status().isUnauthorized());
	}

	@Test
	void メールアドレスは小文字にそろえる() throws Exception {
		Cookie session = signup(newEmail());
		String newEmail = newEmail();

		changeEmail(session, newEmail.toUpperCase(), PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(newEmail));
	}

	@Test
	void メールアドレスを変えると_ほかのブラウザのセッションは使えなくなり_持ち主の名前も変わる() throws Exception {
		String oldEmail = newEmail();
		String newEmail = newEmail();
		Cookie first = signup(oldEmail);
		Cookie second = loginSession(oldEmail);

		Cookie renewed = changeEmail(first, newEmail, PASSWORD)
				.andExpect(status().isOk())
				.andReturn().getResponse().getCookie("SESSION");

		mockMvc.perform(get("/api/boards").cookie(second))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mockMvc.perform(get("/api/boards").cookie(renewed)).andExpect(status().isOk());
		// Spring Session が覚えている持ち主の名前も、新しいアドレスになる（あとで退会などで探せるように）
		assertThat(count("SELECT count(*) FROM spring_session WHERE principal_name = ?", oldEmail)).isZero();
		assertThat(count("SELECT count(*) FROM spring_session WHERE principal_name = ?", newEmail)).isOne();
	}

	@Test
	void メールアドレスを変えたあと退会すると_ほかのブラウザのセッションも消える() throws Exception {
		Cookie first = signup(newEmail());
		String newEmail = newEmail();
		Cookie renewed = changeEmail(first, newEmail, PASSWORD)
				.andExpect(status().isOk())
				.andReturn().getResponse().getCookie("SESSION");
		Cookie second = loginSession(newEmail);

		mockMvc.perform(post("/api/auth/withdraw").with(csrf()).cookie(renewed)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "password": "%s" }
						""".formatted(PASSWORD)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/boards").cookie(second)).andExpect(status().isUnauthorized());
	}

	@Test
	void 今と同じメールアドレスでも成功する() throws Exception {
		String email = newEmail();
		Cookie session = signup(email);

		changeEmail(session, email, PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void ほかの人が登録済みのメールアドレスには変えられない() throws Exception {
		String taken = newEmail();
		signup(taken);
		String email = newEmail();
		Cookie session = signup(email);

		changeEmail(session, taken, PASSWORD)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"))
				.andExpect(jsonPath("$.detail").value("このメールアドレスは登録済みです"));

		mockMvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void パスワードが違うと_登録済みのアドレスでも409ではなく400() throws Exception {
		// パスワードを知らない人に、そのアドレスが登録済みかを教えない
		String taken = newEmail();
		signup(taken);
		Cookie session = signup(newEmail());

		changeEmail(session, taken, "wrong-password")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void メールアドレスの変更でパスワードが違うと400で_アドレスは変わらない() throws Exception {
		String email = newEmail();
		Cookie session = signup(email);

		changeEmail(session, newEmail(), "wrong-password")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("currentPassword"))
				.andExpect(jsonPath("$.errors[0].message").value("パスワードが正しくありません"));

		mockMvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void メールアドレスの形式が正しくないと400() throws Exception {
		Cookie session = signup(newEmail());

		changeEmail(session, "not-an-email", PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("email"))
				.andExpect(jsonPath("$.errors[0].message").value("メールアドレスの形式が正しくありません"));
	}

	@Test
	void ゲストはメールアドレスを変更できない() throws Exception {
		Cookie session = guest();

		changeEmail(session, newEmail(), PASSWORD)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GUEST_NOT_ALLOWED"))
				.andExpect(jsonPath("$.detail").value("ゲストはメールアドレスを変更できません"));
	}

	@Test
	void メールアドレスの変更をユーザーIDでログに残し_メールアドレスは出さない(CapturedOutput output) throws Exception {
		String oldEmail = newEmail();
		String newEmail = newEmail();
		Cookie session = signup(oldEmail);
		long userId = userId(session);

		changeEmail(session, newEmail, PASSWORD).andExpect(status().isOk());

		assertThat(output.getOut()).contains("メールアドレスを変更しました（ユーザーID " + userId + "）");
		assertThat(output.getOut()).doesNotContain(oldEmail).doesNotContain(newEmail);
	}

	// ===== A-21 パスワードの変更 =====

	@Test
	void パスワードを変えると_新しいパスワードでログインでき_古いパスワードではできない() throws Exception {
		String email = newEmail();
		Cookie session = signup(email);

		MvcResult result = changePassword(session, PASSWORD, NEW_PASSWORD)
				.andExpect(status().isNoContent())
				.andReturn();

		// 今の画面はログインしたまま。セッション ID は新しくなる
		Cookie renewed = result.getResponse().getCookie("SESSION");
		assertThat(renewed).isNotNull();
		assertThat(renewed.getValue()).isNotEqualTo(session.getValue());
		mockMvc.perform(get("/api/boards").cookie(renewed)).andExpect(status().isOk());

		login(email, NEW_PASSWORD).andExpect(status().isOk());
		login(email, PASSWORD).andExpect(status().isUnauthorized());
	}

	@Test
	void パスワードを変えると_ほかのブラウザのセッションは使えなくなる() throws Exception {
		String email = newEmail();
		Cookie first = signup(email);
		Cookie second = loginSession(email);
		Cookie other = signup(newEmail());

		Cookie renewed = changePassword(first, PASSWORD, NEW_PASSWORD)
				.andExpect(status().isNoContent())
				.andReturn().getResponse().getCookie("SESSION");

		mockMvc.perform(get("/api/boards").cookie(second))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mockMvc.perform(get("/api/boards").cookie(renewed)).andExpect(status().isOk());
		// ほかの人のセッションは残る
		mockMvc.perform(get("/api/boards").cookie(other)).andExpect(status().isOk());
		assertThat(count("SELECT count(*) FROM spring_session WHERE principal_name = ?", email)).isOne();
	}

	@Test
	void パスワードの変更で今のパスワードが違うと400で_パスワードは変わらない() throws Exception {
		String email = newEmail();
		Cookie session = signup(email);

		changePassword(session, "wrong-password", NEW_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("currentPassword"))
				.andExpect(jsonPath("$.errors[0].message").value("パスワードが正しくありません"));

		login(email, PASSWORD).andExpect(status().isOk());
		// ログインしたまま
		mockMvc.perform(get("/api/boards").cookie(session)).andExpect(status().isOk());
	}

	@Test
	void 新しいパスワードが入力ルールに合わないと400() throws Exception {
		Cookie session = signup(newEmail());

		changePassword(session, PASSWORD, "short")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("newPassword"))
				.andExpect(jsonPath("$.errors[0].message").value("パスワードは8〜64文字の半角英数字・記号で入力してください"));
	}

	@Test
	void ゲストはパスワードを変更できない() throws Exception {
		Cookie session = guest();

		changePassword(session, PASSWORD, NEW_PASSWORD)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GUEST_NOT_ALLOWED"))
				.andExpect(jsonPath("$.detail").value("ゲストはパスワードを変更できません"));
	}

	@Test
	void パスワードの変更をユーザーIDでログに残し_メールアドレスは出さない(CapturedOutput output) throws Exception {
		String email = newEmail();
		Cookie session = signup(email);
		long userId = userId(session);

		changePassword(session, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

		assertThat(output.getOut()).contains("パスワードを変更しました（ユーザーID " + userId + "）");
		assertThat(output.getOut()).doesNotContain(email);
	}

	// ===== 共通：回数制限・ログイン =====

	@Test
	void 今のパスワードが空なら400で_失敗の回数には数えない() throws Exception {
		String ip = newIpAddress();
		Cookie session = signup(newEmail());

		mockMvc.perform(changeEmailRequest(session, newEmail(), "").with(from(ip)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].message").value("パスワードが正しくありません"));
		mockMvc.perform(changePasswordRequest(session, "", NEW_PASSWORD).with(from(ip)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].message").value("パスワードが正しくありません"));

		assertThat(countEvents(ip)).isZero();
	}

	@Test
	void パスワード違いは_ログインの失敗と一緒に数える() throws Exception {
		String ip = newIpAddress();
		String email = newEmail();
		Cookie session = signup(email);
		// ログインの失敗8回 ＋ メールアドレスの変更1回 ＋ パスワードの変更1回 ＝ 10回
		for (int i = 0; i < 8; i++) {
			mockMvc.perform(loginRequest(email, "wrong-password").with(from(ip)))
					.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(changeEmailRequest(session, newEmail(), "wrong-password").with(from(ip)))
				.andExpect(status().isBadRequest());
		mockMvc.perform(changePasswordRequest(session, "wrong-password", NEW_PASSWORD).with(from(ip)))
				.andExpect(status().isBadRequest());
		assertThat(countEvents(ip)).isEqualTo(10);

		// 11回目は、正しいパスワードでも 429
		mockMvc.perform(changeEmailRequest(session, newEmail(), PASSWORD).with(from(ip)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
		mockMvc.perform(changePasswordRequest(session, PASSWORD, NEW_PASSWORD).with(from(ip)))
				.andExpect(status().isTooManyRequests());
	}

	@Test
	void ログインしていないと401() throws Exception {
		mockMvc.perform(put("/api/auth/email").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(emailJson(newEmail(), PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mockMvc.perform(put("/api/auth/password").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(passwordJson(PASSWORD, NEW_PASSWORD)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void CSRFトークンがないと403() throws Exception {
		Cookie session = signup(newEmail());

		mockMvc.perform(put("/api/auth/email").cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(emailJson(newEmail(), PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		mockMvc.perform(put("/api/auth/password").cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(passwordJson(PASSWORD, NEW_PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	// ===== 手助けのメソッド =====

	private static String newEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	// テストごとの IP アドレス（RateLimitApiTest と同じ考え方。2001:db8:3:: を使う）
	private static String newIpAddress() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return "2001:db8:3::%x:%x".formatted(random.nextInt(0x10000), random.nextInt(0x10000));
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

	private Cookie guest() throws Exception {
		Cookie session = mockMvc.perform(post("/api/auth/guest").with(csrf()))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getCookie("SESSION");
		assertThat(session).isNotNull();
		return session;
	}

	private static MockHttpServletRequestBuilder loginRequest(String email, String password) {
		return post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "email": "%s", "password": "%s" }
						""".formatted(email, password));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(loginRequest(email, password));
	}

	// 別のブラウザでログインしたセッション
	private Cookie loginSession(String email) throws Exception {
		Cookie session = login(email, PASSWORD)
				.andExpect(status().isOk())
				.andReturn().getResponse().getCookie("SESSION");
		assertThat(session).isNotNull();
		return session;
	}

	private static MockHttpServletRequestBuilder changeEmailRequest(Cookie session, String email,
			String currentPassword) {
		return put("/api/auth/email").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(emailJson(email, currentPassword));
	}

	private ResultActions changeEmail(Cookie session, String email, String currentPassword) throws Exception {
		return mockMvc.perform(changeEmailRequest(session, email, currentPassword));
	}

	private static MockHttpServletRequestBuilder changePasswordRequest(Cookie session, String currentPassword,
			String newPassword) {
		return put("/api/auth/password").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(passwordJson(currentPassword, newPassword));
	}

	private ResultActions changePassword(Cookie session, String currentPassword, String newPassword)
			throws Exception {
		return mockMvc.perform(changePasswordRequest(session, currentPassword, newPassword));
	}

	private static String emailJson(String email, String currentPassword) {
		return """
				{ "email": "%s", "currentPassword": "%s" }
				""".formatted(email, currentPassword);
	}

	private static String passwordJson(String currentPassword, String newPassword) {
		return """
				{ "currentPassword": "%s", "newPassword": "%s" }
				""".formatted(currentPassword, newPassword);
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
