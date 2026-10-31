package io.github.keiwebcreate.taskboard.card;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Update の API のテスト：A-13 カードの保存、A-09 リスト名の変更、A-07 ボード名の変更。
 * ラベルを作る API はまだないので、ラベルは SQL で直接入れる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UpdateApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// ===== A-13 カードの保存 =====

	@Test
	void カードの全項目を保存できる() throws Exception {
		Cookie session = signup();
		long cardId = addCard(session, "牛乳を買う");
		long work = addLabel(session, "仕事", "#3e63dd");
		long shopping = addLabel(session, "買い物", "#30a46c");

		saveCard(session, cardId, """
				{ "title": "  低脂肪の牛乳を買う  ", "description": "スーパーで\\nhttps://example.com",
				  "importance": "HIGH", "urgency": "LOW", "dueDate": "2026-11-03",
				  "labelIds": [%d, %d] }
				""".formatted(shopping, work))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(cardId))
				.andExpect(jsonPath("$.title").value("低脂肪の牛乳を買う"))
				.andExpect(jsonPath("$.description").value("スーパーで\nhttps://example.com"))
				.andExpect(jsonPath("$.importance").value("HIGH"))
				.andExpect(jsonPath("$.urgency").value("LOW"))
				.andExpect(jsonPath("$.dueDate").value("2026-11-03"))
				.andExpect(jsonPath("$.labelIds.length()").value(2));

		// DB に保存されていて、ボードを読み直しても同じ（ラベルの ID は小さい順）
		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(jsonPath("$.columns[0].cards[0].title").value("低脂肪の牛乳を買う"))
				.andExpect(jsonPath("$.columns[0].cards[0].labelIds[0]").value(work))
				.andExpect(jsonPath("$.columns[0].cards[0].labelIds[1]").value(shopping));
	}

	@Test
	void ラベルは送った一覧で置き換わり_空なら全部外れる() throws Exception {
		Cookie session = signup();
		long cardId = addCard(session, "牛乳を買う");
		long work = addLabel(session, "仕事", "#3e63dd");

		// 同じ ID が2回あっても1つとみなす
		saveCard(session, cardId, cardJson("[%d, %d]".formatted(work, work)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.labelIds.length()").value(1));

		saveCard(session, cardId, cardJson("[]"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.labelIds.length()").value(0));
	}

	@Test
	void 説明が空文字ならnullで保存する() throws Exception {
		Cookie session = signup();
		long cardId = addCard(session, "牛乳を買う");

		saveCard(session, cardId, """
				{ "title": "牛乳を買う", "description": "", "importance": "MEDIUM", "urgency": "MEDIUM",
				  "dueDate": null, "labelIds": [] }
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").isEmpty());
	}

	@Test
	void カードの入力が正しくないと400() throws Exception {
		Cookie session = signup();
		long cardId = addCard(session, "牛乳を買う");

		saveCard(session, cardId, """
				{ "title": " ", "description": "%s", "importance": "MEDIUM", "urgency": "MEDIUM",
				  "dueDate": null, "labelIds": [] }
				""".formatted("あ".repeat(2001)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[?(@.field == 'title')].message").value("タイトルを入力してください"))
				.andExpect(jsonPath("$.errors[?(@.field == 'description')].message")
						.value("説明は2000文字以内で入力してください"));

		// 重要度に決まった値（HIGH・MEDIUM・LOW）以外
		saveCard(session, cardId, """
				{ "title": "牛乳を買う", "importance": "URGENT", "urgency": "MEDIUM", "labelIds": [] }
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void 他人のカードは保存できず404() throws Exception {
		Cookie taro = signup();
		Cookie hanako = signup();
		long taroCardId = addCard(taro, "たろうのカード");

		saveCard(hanako, taroCardId, cardJson("[]"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		mockMvc.perform(get("/api/board").cookie(taro))
				.andExpect(jsonPath("$.columns[0].cards[0].title").value("たろうのカード"));
	}

	@Test
	void 他人のラベルは付けられず404で_カードも変わらない() throws Exception {
		Cookie taro = signup();
		Cookie hanako = signup();
		long taroCardId = addCard(taro, "たろうのカード");
		long hanakoLabel = addLabel(hanako, "はなこのラベル", "#e5484d");

		saveCard(taro, taroCardId, cardJson("[%d]".formatted(hanakoLabel)))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/board").cookie(taro))
				.andExpect(jsonPath("$.columns[0].cards[0].title").value("たろうのカード"))
				.andExpect(jsonPath("$.columns[0].cards[0].labelIds.length()").value(0));
	}

	// ===== A-09 リスト名の変更 =====

	@Test
	void リスト名を変更できる() throws Exception {
		Cookie session = signup();
		long columnId = firstColumnId(session);

		renameColumn(session, columnId, "  やること  ")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(columnId))
				.andExpect(jsonPath("$.name").value("やること"));

		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(jsonPath("$.columns[0].name").value("やること"));
	}

	@Test
	void リスト名が空なら400_他人のリストなら404() throws Exception {
		Cookie taro = signup();
		Cookie hanako = signup();
		long taroColumnId = firstColumnId(taro);

		renameColumn(taro, taroColumnId, "")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].message").value("リスト名を入力してください"));

		renameColumn(hanako, taroColumnId, "のっとり")
				.andExpect(status().isNotFound());
	}

	// ===== A-07 ボード名の変更 =====

	@Test
	void ボード名を変更できる() throws Exception {
		Cookie session = signup();

		renameBoard(session, " 仕事用ボード ")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value("仕事用ボード"));

		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(jsonPath("$.name").value("仕事用ボード"));
	}

	@Test
	void ボード名が51文字以上なら400() throws Exception {
		Cookie session = signup();

		renameBoard(session, "あ".repeat(51))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].message").value("ボード名は50文字以内で入力してください"));
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

	private long firstColumnId(Cookie session) throws Exception {
		String json = mockMvc.perform(get("/api/board").cookie(session))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(json, "$.columns[0].id").longValue();
	}

	// 一番左のリストにカードを足して、その ID を返す
	private long addCard(Cookie session, String title) throws Exception {
		String json = mockMvc.perform(post("/api/columns/{id}/cards", firstColumnId(session)).with(csrf())
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "title": "%s" }
						""".formatted(title)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(json, "$.id").longValue();
	}

	// ラベルを作る API はまだないので、SQL で直接入れて ID を返す
	private long addLabel(Cookie session, String name, String color) throws Exception {
		// queryForObject は null を返すこともある（型の上では）ので、null でないことを確かめてから使う
		return Objects.requireNonNull(jdbcTemplate.queryForObject(
				"INSERT INTO labels (user_id, name, color) VALUES (?, ?, ?) RETURNING id",
				Long.class, userId(session), name, color));
	}

	private static String cardJson(String labelIds) {
		return """
				{ "title": "牛乳を買う", "description": null, "importance": "MEDIUM", "urgency": "MEDIUM",
				  "dueDate": null, "labelIds": %s }
				""".formatted(labelIds);
	}

	private ResultActions saveCard(Cookie session, long cardId, String json) throws Exception {
		return mockMvc.perform(put("/api/cards/{id}", cardId).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	private ResultActions renameColumn(Cookie session, long columnId, String name) throws Exception {
		return mockMvc.perform(patch("/api/columns/{id}", columnId).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "%s" }
						""".formatted(name)));
	}

	private ResultActions renameBoard(Cookie session, String name) throws Exception {
		return mockMvc.perform(patch("/api/board").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "%s" }
						""".formatted(name)));
	}

}
