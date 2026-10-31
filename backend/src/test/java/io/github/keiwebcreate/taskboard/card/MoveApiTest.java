package io.github.keiwebcreate.taskboard.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import io.github.keiwebcreate.taskboard.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;

/**
 * 並び替えの API のテスト：A-15 カードの並び替え・移動、A-11 リストの並び替え。
 * 並び順（position）は API では返さないので、振り直しは DB を直接見て確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MoveApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// ===== A-15 カードの並び替え・移動 =====

	@Test
	void 同じリストの中で_カードを下へ動かせる() throws Exception {
		Cookie session = signup();
		long columnId = columnId(session, 0);
		long a = addCard(session, columnId, "A");
		addCard(session, columnId, "B");
		addCard(session, columnId, "C");

		moveCard(session, a, columnId, 2).andExpect(status().isNoContent());

		assertThat(cardTitles(columnId)).containsExactly("B", "C", "A");
		assertThat(cardPositions(columnId)).containsExactly(0, 1, 2);
	}

	@Test
	void 同じリストの中で_カードを上へ動かせる() throws Exception {
		Cookie session = signup();
		long columnId = columnId(session, 0);
		addCard(session, columnId, "A");
		addCard(session, columnId, "B");
		long c = addCard(session, columnId, "C");

		moveCard(session, c, columnId, 0).andExpect(status().isNoContent());

		assertThat(cardTitles(columnId)).containsExactly("C", "A", "B");
		assertThat(cardPositions(columnId)).containsExactly(0, 1, 2);
	}

	@Test
	void 別のリストの間に移すと_両方のリストの並び順が付け直される() throws Exception {
		Cookie session = signup();
		long todo = columnId(session, 0);
		long doing = columnId(session, 1);
		addCard(session, todo, "W");
		long x = addCard(session, todo, "X");
		addCard(session, todo, "Y");
		addCard(session, doing, "A");
		addCard(session, doing, "B");

		// API設計書 A-15 の例：「A, B」へ X を 1 で移す → A, X, B
		moveCard(session, x, doing, 1).andExpect(status().isNoContent());

		assertThat(cardTitles(doing)).containsExactly("A", "X", "B");
		assertThat(cardPositions(doing)).containsExactly(0, 1, 2);
		// 移動元は、抜けた番号が詰められている
		assertThat(cardTitles(todo)).containsExactly("W", "Y");
		assertThat(cardPositions(todo)).containsExactly(0, 1);
		// ボードの取得でも、移した先のリストに入っている
		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(jsonPath("$.columns[1].cards[*].title").value(contains("A", "X", "B")));
	}

	@Test
	void 別のリストへは_今の枚数と同じ位置で一番下に_空のリストにも移せる() throws Exception {
		Cookie session = signup();
		long todo = columnId(session, 0);
		long doing = columnId(session, 1);
		long done = columnId(session, 2);
		long x = addCard(session, todo, "X");
		long y = addCard(session, todo, "Y");
		addCard(session, doing, "A");

		moveCard(session, x, doing, 1).andExpect(status().isNoContent());
		moveCard(session, y, done, 0).andExpect(status().isNoContent());

		assertThat(cardTitles(doing)).containsExactly("A", "X");
		assertThat(cardTitles(done)).containsExactly("Y");
		assertThat(cardTitles(todo)).isEmpty();
	}

	@Test
	void 位置が範囲の外なら400で_並びは変わらない() throws Exception {
		Cookie session = signup();
		long todo = columnId(session, 0);
		long doing = columnId(session, 1);
		long a = addCard(session, todo, "A");
		addCard(session, todo, "B");
		addCard(session, doing, "C");

		// 同じリスト内は 0〜枚数−1（2枚なら 0〜1）
		moveCard(session, a, todo, 2)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_POSITION"))
				.andExpect(jsonPath("$.detail").value("移動先の位置が正しくありません"));
		moveCard(session, a, todo, -1).andExpect(status().isBadRequest());
		// 別のリストへは 0〜枚数（1枚なら 0〜1）
		moveCard(session, a, doing, 2)
				.andExpect(jsonPath("$.code").value("INVALID_POSITION"));

		assertThat(cardTitles(todo)).containsExactly("A", "B");
		assertThat(cardTitles(doing)).containsExactly("C");
	}

	@Test
	void 位置や移動先が送られてこなければ400() throws Exception {
		Cookie session = signup();
		long todo = columnId(session, 0);
		long a = addCard(session, todo, "A");

		mockMvc.perform(put("/api/cards/{id}/position", a).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors.length()").value(2));
	}

	@Test
	void 満杯のリストへは移せず409_同じリストの中なら満杯でも並び替えられる() throws Exception {
		Cookie session = signup();
		long todo = columnId(session, 0);
		long doing = columnId(session, 1);
		long x = addCard(session, todo, "X");
		// 100枚を API で足すと時間がかかるので、DB に直接入れる
		jdbcTemplate.update("""
				INSERT INTO cards (column_id, title, position)
				SELECT ?, 'カード' || n, n FROM generate_series(0, 99) AS n
				""", doing);
		long last = lastCardId(doing);

		moveCard(session, x, doing, 0)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"))
				.andExpect(jsonPath("$.detail").value("このリストのカードは100枚までです"));
		assertThat(cardTitles(todo)).containsExactly("X");

		moveCard(session, last, doing, 0).andExpect(status().isNoContent());
		assertThat(cardTitles(doing).getFirst()).isEqualTo("カード99");
	}

	@Test
	void 他人のカードや他人のリストへは動かせず404() throws Exception {
		Cookie taro = signup();
		Cookie hanako = signup();
		long taroColumn = columnId(taro, 0);
		long taroCard = addCard(taro, taroColumn, "たろうのカード");
		long hanakoColumn = columnId(hanako, 0);
		long hanakoCard = addCard(hanako, hanakoColumn, "はなこのカード");

		// 他人のカードを動かす
		moveCard(hanako, taroCard, hanakoColumn, 0)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		// 自分のカードを他人のリストへ移す
		moveCard(hanako, hanakoCard, taroColumn, 0).andExpect(status().isNotFound());
		// 存在しないカード・リスト
		moveCard(hanako, 999_999_999L, hanakoColumn, 0).andExpect(status().isNotFound());
		moveCard(hanako, hanakoCard, 999_999_999L, 0).andExpect(status().isNotFound());

		assertThat(cardTitles(taroColumn)).containsExactly("たろうのカード");
		assertThat(cardTitles(hanakoColumn)).containsExactly("はなこのカード");
	}

	// ===== A-11 リストの並び替え =====

	@Test
	void リストを右へ動かせる() throws Exception {
		Cookie session = signup();
		long todo = columnId(session, 0);

		// API設計書 A-11 の例：「A, B, C」の A を 2 に動かす → B, C, A
		moveColumn(session, todo, 2).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(jsonPath("$.columns[*].name").value(contains("作業中", "完了", "未着手")));
		assertThat(columnPositions(session)).containsExactly(0, 1, 2);
	}

	@Test
	void リストを左へ動かせる() throws Exception {
		Cookie session = signup();
		long done = columnId(session, 2);

		moveColumn(session, done, 0).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(jsonPath("$.columns[*].name").value(contains("完了", "未着手", "作業中")));
		assertThat(columnPositions(session)).containsExactly(0, 1, 2);
	}

	@Test
	void リストの位置が範囲の外なら400で_並びは変わらない() throws Exception {
		Cookie session = signup();
		long todo = columnId(session, 0);

		// 3個なら 0〜2
		moveColumn(session, todo, 3)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_POSITION"));
		moveColumn(session, todo, -1).andExpect(status().isBadRequest());
		mockMvc.perform(put("/api/columns/{id}/position", todo).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(jsonPath("$.columns[*].name").value(contains("未着手", "作業中", "完了")));
	}

	@Test
	void 他人のリストは動かせず404() throws Exception {
		Cookie taro = signup();
		Cookie hanako = signup();

		moveColumn(hanako, columnId(taro, 0), 2).andExpect(status().isNotFound());

		mockMvc.perform(get("/api/board").cookie(taro))
				.andExpect(jsonPath("$.columns[*].name").value(contains("未着手", "作業中", "完了")));
	}

	// ===== 手助けのメソッド =====

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

	private long userId(Cookie session) throws Exception {
		String json = mockMvc.perform(get("/api/auth/me").cookie(session))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(json, "$.id").longValue();
	}

	// 左から index 番目（0から）のリストの ID
	private long columnId(Cookie session, int index) throws Exception {
		String json = mockMvc.perform(get("/api/board").cookie(session))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(json, "$.columns[" + index + "].id").longValue();
	}

	private long addCard(Cookie session, long columnId, String title) throws Exception {
		String json = mockMvc.perform(post("/api/columns/{id}/cards", columnId).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "title": "%s" }
						""".formatted(title)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(json, "$.id").longValue();
	}

	private ResultActions moveCard(Cookie session, long cardId, long columnId, int position) throws Exception {
		return mockMvc.perform(put("/api/cards/{id}/position", cardId).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "columnId": %d, "position": %d }
						""".formatted(columnId, position)));
	}

	private ResultActions moveColumn(Cookie session, long columnId, int position) throws Exception {
		return mockMvc.perform(put("/api/columns/{id}/position", columnId).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "position": %d }
						""".formatted(position)));
	}

	// リストのカードのタイトルを、上から順に
	private List<String> cardTitles(long columnId) {
		return jdbcTemplate.queryForList(
				"SELECT title FROM cards WHERE column_id = ? ORDER BY position", String.class, columnId);
	}

	// リストのカードの position を、上から順に
	private List<Integer> cardPositions(long columnId) {
		return jdbcTemplate.queryForList(
				"SELECT position FROM cards WHERE column_id = ? ORDER BY position", Integer.class, columnId);
	}

	// リストの一番下のカードの ID
	private long lastCardId(long columnId) {
		return jdbcTemplate.queryForList(
				"SELECT id FROM cards WHERE column_id = ? ORDER BY position DESC LIMIT 1", Long.class, columnId)
				.getFirst();
	}

	// ボードのリストの position を、左から順に
	private List<Integer> columnPositions(Cookie session) throws Exception {
		return jdbcTemplate.queryForList("""
				SELECT c.position FROM board_columns c JOIN boards b ON b.id = c.board_id
				WHERE b.user_id = ? ORDER BY c.position
				""", Integer.class, userId(session));
	}

}
