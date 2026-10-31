package io.github.keiwebcreate.taskboard.column;

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
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import io.github.keiwebcreate.taskboard.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;

/**
 * A-08 リストの追加と A-12 カードの追加のテスト。
 * 上限のテストのために、このテストだけ上限を小さくしている（リスト4個・1リストのカード2枚）。
 * 登録したときにリストが3つできるので、あと1つでリストは上限になる。
 */
@SpringBootTest(properties = { "app.limits.columns=4", "app.limits.cards-per-column=2" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CreateColumnAndCardApiTest {

	@Autowired
	private MockMvc mockMvc;

	// ===== A-08 リストの追加 =====

	@Test
	void リストを追加すると右端に並ぶ() throws Exception {
		Cookie session = signup();

		addColumn(session, "  保留  ")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value("保留")); // 前後の空白は取り除く

		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(jsonPath("$.columns.length()").value(4))
				.andExpect(jsonPath("$.columns[3].name").value("保留"))
				.andExpect(jsonPath("$.columns[3].cards.length()").value(0));
	}

	@Test
	void リスト名が空や51文字以上なら400() throws Exception {
		Cookie session = signup();

		addColumn(session, "   ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("name"))
				.andExpect(jsonPath("$.errors[0].message").value("リスト名を入力してください"));

		addColumn(session, "あ".repeat(51))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].message").value("リスト名は50文字以内で入力してください"));
	}

	@Test
	void リストが上限に達していたら409() throws Exception {
		Cookie session = signup();
		addColumn(session, "4つ目").andExpect(status().isCreated());

		addColumn(session, "5つ目")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"))
				.andExpect(jsonPath("$.detail").value("リストは4個までです"));
	}

	@Test
	void ログインしていないとリストを追加できない() throws Exception {
		mockMvc.perform(post("/api/columns").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "保留" }
						"""))
				.andExpect(status().isUnauthorized());
	}

	// ===== A-12 カードの追加 =====

	@Test
	void カードを追加するとリストの一番下に並ぶ() throws Exception {
		Cookie session = signup();
		long todoId = firstColumnId(session);

		addCard(session, todoId, "1枚目").andExpect(status().isCreated());
		addCard(session, todoId, "  2枚目  ")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("2枚目"))
				// 追加したばかりのカードは、重要度・緊急度が「中」で、ほかは空
				.andExpect(jsonPath("$.importance").value("MEDIUM"))
				.andExpect(jsonPath("$.urgency").value("MEDIUM"))
				.andExpect(jsonPath("$.description").isEmpty())
				.andExpect(jsonPath("$.dueDate").isEmpty())
				.andExpect(jsonPath("$.labelIds.length()").value(0));

		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(jsonPath("$.columns[0].cards[0].title").value("1枚目"))
				.andExpect(jsonPath("$.columns[0].cards[1].title").value("2枚目"));
	}

	@Test
	void タイトルが空や101文字以上なら400() throws Exception {
		Cookie session = signup();
		long todoId = firstColumnId(session);

		addCard(session, todoId, "")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("title"))
				.andExpect(jsonPath("$.errors[0].message").value("タイトルを入力してください"));

		addCard(session, todoId, "あ".repeat(101))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].message").value("タイトルは100文字以内で入力してください"));
	}

	@Test
	void リストのカードが上限に達していたら409() throws Exception {
		Cookie session = signup();
		long todoId = firstColumnId(session);
		addCard(session, todoId, "1枚目").andExpect(status().isCreated());
		addCard(session, todoId, "2枚目").andExpect(status().isCreated());

		addCard(session, todoId, "3枚目")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"))
				.andExpect(jsonPath("$.detail").value("このリストのカードは2枚までです"));
	}

	@Test
	void 他人のリストにはカードを追加できず404() throws Exception {
		Cookie taro = signup();
		Cookie hanako = signup();
		long taroColumnId = firstColumnId(taro);

		addCard(hanako, taroColumnId, "のぞき見")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		// たろうのリストには何も増えていない
		mockMvc.perform(get("/api/board").cookie(taro))
				.andExpect(jsonPath("$.columns[0].cards.length()").value(0));
	}

	@Test
	void 存在しないリストなら404() throws Exception {
		Cookie session = signup();

		addCard(session, 999_999_999L, "どこにもない")
				.andExpect(status().isNotFound());
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

	// ボードの一番左のリスト（未着手）の ID
	private long firstColumnId(Cookie session) throws Exception {
		String json = mockMvc.perform(get("/api/board").cookie(session))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(json, "$.columns[0].id").longValue();
	}

	private ResultActions addColumn(Cookie session, String name) throws Exception {
		return mockMvc.perform(post("/api/columns").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "%s" }
						""".formatted(name)));
	}

	private ResultActions addCard(Cookie session, long columnId, String title) throws Exception {
		return mockMvc.perform(post("/api/columns/{columnId}/cards", columnId).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "title": "%s" }
						""".formatted(title)));
	}

}
