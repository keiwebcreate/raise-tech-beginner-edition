package io.github.keiwebcreate.taskboard.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Objects;
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
 * Delete の API のテスト：A-14 カードの削除、A-10 リストの削除。
 * 並び順（position）は API では返さないので、振り直しは DB を直接見て確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DeleteApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// ===== A-14 カードの削除 =====

	@Test
	void カードを削除すると_残りのカードの並び順が詰められる() throws Exception {
		Cookie session = signup();
		long columnId = columnId(session, 0);
		addCard(session, columnId, "A");
		long b = addCard(session, columnId, "B");
		addCard(session, columnId, "C");
		addCard(session, columnId, "D");

		deleteCard(session, b).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(jsonPath("$.columns[0].cards[*].title").value(contains("A", "C", "D")));
		// 真ん中を消しても、番号に抜けがない（0, 2, 3 ではなく 0, 1, 2）
		assertThat(cardPositions(columnId)).containsExactly(0, 1, 2);
	}

	@Test
	void カードを削除したあとに追加すると一番下に入る() throws Exception {
		Cookie session = signup();
		long columnId = columnId(session, 0);
		long a = addCard(session, columnId, "A");
		addCard(session, columnId, "B");
		deleteCard(session, a).andExpect(status().isNoContent());

		addCard(session, columnId, "C");

		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(jsonPath("$.columns[0].cards[*].title").value(contains("B", "C")));
		assertThat(cardPositions(columnId)).containsExactly(0, 1);
	}

	@Test
	void 他人のカードや存在しないカードは削除できず404() throws Exception {
		Cookie taro = signup();
		Cookie hanako = signup();
		long taroCardId = addCard(taro, columnId(taro, 0), "たろうのカード");

		deleteCard(hanako, taroCardId)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		deleteCard(taro, 999_999_999L).andExpect(status().isNotFound());

		// たろうのカードは残っている
		mockMvc.perform(get("/api/board").cookie(taro))
				.andExpect(jsonPath("$.columns[0].cards.length()").value(1));
	}

	@Test
	void ラベルの付いたカードも削除でき_ラベル自体は残る() throws Exception {
		Cookie session = signup();
		long cardId = addCard(session, columnId(session, 0), "ラベル付き");
		// queryForObject は null を返すこともある（型の上では）ので、null でないことを確かめてから使う
		long labelId = Objects.requireNonNull(jdbcTemplate.queryForObject(
				"INSERT INTO labels (user_id, name, color) VALUES (?, '仕事', '#3e63dd') RETURNING id",
				Long.class, userId(session)));
		jdbcTemplate.update("INSERT INTO card_labels (card_id, label_id) VALUES (?, ?)", cardId, labelId);

		deleteCard(session, cardId).andExpect(status().isNoContent());

		assertThat(jdbcTemplate.queryForObject(
				"SELECT count(*) FROM card_labels WHERE card_id = ?", Long.class, cardId))
				.isZero();
		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(jsonPath("$.labels.length()").value(1));
	}

	// ===== A-10 リストの削除 =====

	@Test
	void リストを削除すると中のカードも消え_残りのリストの並び順が詰められる() throws Exception {
		Cookie session = signup();
		long doingId = columnId(session, 1); // 作業中
		addCard(session, doingId, "作業中のカード1");
		addCard(session, doingId, "作業中のカード2");

		deleteColumn(session, doingId).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(jsonPath("$.columns[*].name").value(contains("未着手", "完了")));
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM cards WHERE column_id = ?", Long.class, doingId))
				.isZero();
		assertThat(columnPositions(session)).containsExactly(0, 1);
	}

	@Test
	void リストは0個になってもよく_そのあと追加すると左端から並ぶ() throws Exception {
		Cookie session = signup();
		long first = columnId(session, 0);
		long second = columnId(session, 1);
		long third = columnId(session, 2);

		deleteColumn(session, second).andExpect(status().isNoContent());
		deleteColumn(session, first).andExpect(status().isNoContent());
		deleteColumn(session, third).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(jsonPath("$.columns.length()").value(0));

		mockMvc.perform(post("/api/columns").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "新しいリスト" }
						"""))
				.andExpect(status().isCreated());
		assertThat(columnPositions(session)).containsExactly(0);
	}

	@Test
	void 他人のリストは削除できず404() throws Exception {
		Cookie taro = signup();
		Cookie hanako = signup();

		deleteColumn(hanako, columnId(taro, 0)).andExpect(status().isNotFound());

		mockMvc.perform(get("/api/board").cookie(taro))
				.andExpect(jsonPath("$.columns.length()").value(3));
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

	private ResultActions deleteCard(Cookie session, long cardId) throws Exception {
		return mockMvc.perform(delete("/api/cards/{id}", cardId).with(csrf()).cookie(session));
	}

	private ResultActions deleteColumn(Cookie session, long columnId) throws Exception {
		return mockMvc.perform(delete("/api/columns/{id}", columnId).with(csrf()).cookie(session));
	}

	// リストのカードの position を、上から順に
	private List<Integer> cardPositions(long columnId) {
		return jdbcTemplate.queryForList(
				"SELECT position FROM cards WHERE column_id = ? ORDER BY position", Integer.class, columnId);
	}

	// ボードのリストの position を、左から順に
	private List<Integer> columnPositions(Cookie session) throws Exception {
		return jdbcTemplate.queryForList("""
				SELECT c.position FROM board_columns c JOIN boards b ON b.id = c.board_id
				WHERE b.user_id = ? ORDER BY c.position
				""", Integer.class, userId(session));
	}

}
