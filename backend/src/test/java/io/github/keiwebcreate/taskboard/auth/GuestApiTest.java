package io.github.keiwebcreate.taskboard.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import io.github.keiwebcreate.taskboard.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;

/**
 * ゲストのテスト：A-03 ゲストログイン、A-04 ゲストのログアウトでの削除、24時間たったゲストの削除、
 * 消えたあとの A-05（要件 U-06〜U-08、詳細設計書 3.8.5・3.8.6）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GuestApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private GuestCleanupJob guestCleanupJob;

	// ===== A-03 ゲストログイン =====

	@Test
	void ゲストログインすると_ゲストとしてログインした状態になる() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/guest").with(csrf()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.isGuest").value(true))
				.andExpect(jsonPath("$.email").value(matchesPattern("guest-[0-9a-f-]{36}@guest\\.local")))
				.andReturn();
		Cookie session = result.getResponse().getCookie("SESSION");

		mockMvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isGuest").value(true));
	}

	@Test
	void ゲストのボードには_サンプルのリストとカードとラベルが入っている() throws Exception {
		Cookie session = guest();
		LocalDate today = LocalDate.now(GuestSampleData.JAPAN);

		String json = mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("マイボード"))
				.andExpect(jsonPath("$.labels[*].name").value(contains("サンプル", "仕事", "プライベート")))
				.andExpect(jsonPath("$.columns[*].name").value(contains("未着手", "作業中", "完了")))
				.andExpect(jsonPath("$.columns[0].cards[*].title").value(
						contains("サンプル：右のリストへドラッグしてみよう", "会議の資料を準備する", "旅行の計画を立てる")))
				.andExpect(jsonPath("$.columns[0].cards[1].importance").value("HIGH"))
				.andExpect(jsonPath("$.columns[0].cards[1].urgency").value("HIGH"))
				// 期限は日本の日付で「今日」と「20日後」
				.andExpect(jsonPath("$.columns[0].cards[1].dueDate").value(today.toString()))
				.andExpect(jsonPath("$.columns[0].cards[2].dueDate").value(today.plusDays(20).toString()))
				.andExpect(jsonPath("$.columns[1].cards[0].title").value("カードをクリックして詳細を編集"))
				.andExpect(jsonPath("$.columns[1].cards[0].description").value(
						"タイトル・説明・重要度・緊急度・期限・ラベルを編集できます。\n詳しくは https://example.com"))
				.andExpect(jsonPath("$.columns[2].cards.length()").value(0))
				.andReturn().getResponse().getContentAsString();

		// どのカードにもラベルが1つ付いている（「会議の資料」には「仕事」）
		long workLabelId = JsonPath.<Number>read(json, "$.labels[1].id").longValue();
		long cardLabelId = JsonPath.<Number>read(json, "$.columns[0].cards[1].labelIds[0]").longValue();
		assertThat(cardLabelId).isEqualTo(workLabelId);
		// 並び順は 0 からの連番
		assertThat(jdbcTemplate.queryForList("""
				SELECT c.position FROM cards c JOIN board_columns bc ON bc.id = c.column_id
				WHERE bc.id = ? ORDER BY c.position
				""", Integer.class, JsonPath.<Number>read(json, "$.columns[0].id").longValue()))
				.containsExactly(0, 1, 2);
	}

	@Test
	void 押すたびに別のゲストができ_ほかのゲストのデータは見えない() throws Exception {
		Cookie first = guest();
		Cookie second = guest();

		assertThat(userId(first)).isNotEqualTo(userId(second));
		assertThat(boardId(first)).isNotEqualTo(boardId(second));
	}

	@Test
	void CSRFトークンがないとゲストは作れない() throws Exception {
		mockMvc.perform(post("/api/auth/guest"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	@Test
	void ゲストのメールアドレスではログインできない() throws Exception {
		Cookie session = guest();
		String email = email(session);

		mockMvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "email": "%s", "password": "password123" }
						""".formatted(email)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
	}

	// ===== A-04 ゲストのログアウト =====

	@Test
	void ゲストがログアウトすると_ユーザーとボードが消える() throws Exception {
		Cookie session = guest();
		long userId = userId(session);

		mockMvc.perform(post("/api/auth/logout").with(csrf()).cookie(session))
				.andExpect(status().isNoContent());

		assertThat(count("SELECT count(*) FROM users WHERE id = ?", userId)).isZero();
		// ボード・リスト・カード・ラベルは ON DELETE CASCADE で一緒に消える
		assertThat(count("SELECT count(*) FROM boards WHERE user_id = ?", userId)).isZero();
		assertThat(count("SELECT count(*) FROM labels WHERE user_id = ?", userId)).isZero();
	}

	@Test
	void 登録したユーザーがログアウトしても_データは消えない() throws Exception {
		Cookie session = signup();
		long userId = userId(session);

		mockMvc.perform(post("/api/auth/logout").with(csrf()).cookie(session))
				.andExpect(status().isNoContent());

		assertThat(count("SELECT count(*) FROM users WHERE id = ?", userId)).isOne();
		assertThat(count("SELECT count(*) FROM boards WHERE user_id = ?", userId)).isOne();
	}

	// ログアウトのあとのゲストログイン（CSRF トークンの Cookie）は CsrfCookieTest で確かめる

	// ===== 24時間たったゲストの削除 =====

	@Test
	void 作ってから24時間たったゲストだけが消える() throws Exception {
		long oldGuest = userId(guest());
		long newGuest = userId(guest());
		long oldUser = userId(signup());
		setCreatedHoursAgo(oldGuest, 25);
		setCreatedHoursAgo(newGuest, 23);
		setCreatedHoursAgo(oldUser, 25);

		guestCleanupJob.deleteExpiredGuests();

		assertThat(count("SELECT count(*) FROM users WHERE id = ?", oldGuest)).isZero();
		assertThat(count("SELECT count(*) FROM boards WHERE user_id = ?", oldGuest)).isZero();
		// 24時間たっていないゲストと、登録したユーザーは残る
		assertThat(count("SELECT count(*) FROM users WHERE id = ?", newGuest)).isOne();
		assertThat(count("SELECT count(*) FROM users WHERE id = ?", oldUser)).isOne();
	}

	@Test
	void 消されたゲストのセッションでは_meが401になりセッションも消える() throws Exception {
		Cookie session = guest();
		long userId = userId(session);
		setCreatedHoursAgo(userId, 25);
		guestCleanupJob.deleteExpiredGuests();

		// ボードなどは見つからないので 404
		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(status().isNotFound());
		// /me は 401 にして、セッションの Cookie を消す（画面はログイン画面へ移る）
		mockMvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
				.andExpect(cookie().maxAge("SESSION", 0));
		// セッションは破棄されたので、同じ Cookie ではログインしていない扱い
		mockMvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(status().isUnauthorized());
	}

	// ===== 手助けのメソッド =====

	private Cookie guest() throws Exception {
		return mockMvc.perform(post("/api/auth/guest").with(csrf()))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getCookie("SESSION");
	}

	private Cookie signup() throws Exception {
		String email = "user-" + UUID.randomUUID() + "@example.com";
		return mockMvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "email": "%s", "password": "password123" }
						""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getCookie("SESSION");
	}

	private String me(Cookie session) throws Exception {
		return mockMvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private long userId(Cookie session) throws Exception {
		return JsonPath.<Number>read(me(session), "$.id").longValue();
	}

	private String email(Cookie session) throws Exception {
		return JsonPath.read(me(session), "$.email");
	}

	private long boardId(Cookie session) throws Exception {
		String json = mockMvc.perform(get("/api/board").cookie(session))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(json, "$.id").longValue();
	}

	private void setCreatedHoursAgo(long userId, int hours) {
		jdbcTemplate.update("UPDATE users SET created_at = now() - make_interval(hours => ?) WHERE id = ?", hours,
				userId);
	}

	private Long count(String sql, long userId) {
		return jdbcTemplate.queryForObject(sql, Long.class, userId);
	}

}
