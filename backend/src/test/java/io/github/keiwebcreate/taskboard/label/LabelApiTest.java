package io.github.keiwebcreate.taskboard.label;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * ラベルの API のテスト：A-16 追加、A-17 変更、A-18 削除（API設計書 5.5、要件 T-01・T-03）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LabelApiTest {

	@Autowired
	private MockMvc mockMvc;

	// ===== A-16 ラベルの追加 =====

	@Test
	void ラベルを追加すると_前後の空白を除いて保存され_ボードに作成順で並ぶ() throws Exception {
		Cookie session = signup();

		addLabel(session, "  仕事  ", "#3e63dd")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value("仕事"))
				.andExpect(jsonPath("$.color").value("#3e63dd"));
		addLabel(session, "買い物", "#30a46c").andExpect(status().isCreated());

		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(jsonPath("$.labels.length()").value(2))
				.andExpect(jsonPath("$.labels[0].name").value("仕事"))
				.andExpect(jsonPath("$.labels[1].name").value("買い物"));
	}

	@Test
	void 名前が空や21文字以上なら400() throws Exception {
		Cookie session = signup();

		addLabel(session, " ", "#3e63dd")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("name"))
				.andExpect(jsonPath("$.errors[0].message").value("ラベル名を入力してください"));
		addLabel(session, "あ".repeat(21), "#3e63dd")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].message").value("ラベル名は20文字以内で入力してください"));
	}

	@Test
	void 色が8色のどれでもなければ400() throws Exception {
		Cookie session = signup();

		addLabel(session, "仕事", "#123456")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("color"))
				.andExpect(jsonPath("$.errors[0].message").value("色を選んでください"));
	}

	@Test
	void 同じ名前のラベルがあれば409() throws Exception {
		Cookie session = signup();
		addLabel(session, "仕事", "#3e63dd").andExpect(status().isCreated());

		addLabel(session, " 仕事 ", "#e5484d")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LABEL_NAME_DUPLICATED"))
				.andExpect(jsonPath("$.detail").value("同じ名前のラベルがあります"));
	}

	@Test
	void 他人と同じ名前のラベルは作れる() throws Exception {
		addLabel(signup(), "仕事", "#3e63dd").andExpect(status().isCreated());
		addLabel(signup(), "仕事", "#3e63dd").andExpect(status().isCreated());
	}

	@Test
	void ラベルは30個まで() throws Exception {
		Cookie session = signup();
		for (int i = 1; i <= 30; i++) {
			addLabel(session, "ラベル" + i, "#3e63dd").andExpect(status().isCreated());
		}

		addLabel(session, "ラベル31", "#3e63dd")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"))
				.andExpect(jsonPath("$.detail").value("ラベルは30個までです"));
	}

	// ===== A-17 ラベルの変更 =====

	@Test
	void ラベルの名前と色を変更できる() throws Exception {
		Cookie session = signup();
		long labelId = idOf(addLabel(session, "仕事", "#3e63dd"));

		updateLabel(session, labelId, " 急ぎ ", "#e5484d")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(labelId))
				.andExpect(jsonPath("$.name").value("急ぎ"))
				.andExpect(jsonPath("$.color").value("#e5484d"));

		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(jsonPath("$.labels[0].name").value("急ぎ"))
				.andExpect(jsonPath("$.labels[0].color").value("#e5484d"));
	}

	@Test
	void 名前を変えずに色だけ変えるのは重複とみなさない() throws Exception {
		Cookie session = signup();
		long labelId = idOf(addLabel(session, "仕事", "#3e63dd"));

		updateLabel(session, labelId, "仕事", "#30a46c")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.color").value("#30a46c"));
	}

	@Test
	void ほかのラベルと同じ名前には変更できない() throws Exception {
		Cookie session = signup();
		addLabel(session, "仕事", "#3e63dd");
		long labelId = idOf(addLabel(session, "買い物", "#30a46c"));

		updateLabel(session, labelId, "仕事", "#30a46c")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LABEL_NAME_DUPLICATED"));
	}

	@Test
	void 変更でも入力チェックをする() throws Exception {
		Cookie session = signup();
		long labelId = idOf(addLabel(session, "仕事", "#3e63dd"));

		updateLabel(session, labelId, "", "#3e63dd").andExpect(status().isBadRequest());
		updateLabel(session, labelId, "仕事", "#ffffff")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("color"));
	}

	@Test
	void 他人のラベルは変更できず404() throws Exception {
		long othersLabel = idOf(addLabel(signup(), "仕事", "#3e63dd"));

		updateLabel(signup(), othersLabel, "乗っ取り", "#e5484d")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	// ===== A-18 ラベルの削除 =====

	@Test
	void ラベルを削除すると_付いていたカードから外れ_カードは残る() throws Exception {
		Cookie session = signup();
		long work = idOf(addLabel(session, "仕事", "#3e63dd"));
		long shopping = idOf(addLabel(session, "買い物", "#30a46c"));
		long cardId = addCard(session, "牛乳を買う");
		mockMvc.perform(put("/api/cards/{id}", cardId).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "title": "牛乳を買う", "description": null, "importance": "MEDIUM", "urgency": "MEDIUM",
						  "dueDate": null, "labelIds": [%d, %d] }
						""".formatted(work, shopping)))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/api/labels/{id}", work).with(csrf()).cookie(session))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/board").cookie(session))
				.andExpect(jsonPath("$.labels.length()").value(1))
				.andExpect(jsonPath("$.labels[0].id").value(shopping))
				.andExpect(jsonPath("$.columns[0].cards.length()").value(1))
				.andExpect(jsonPath("$.columns[0].cards[0].labelIds.length()").value(1))
				.andExpect(jsonPath("$.columns[0].cards[0].labelIds[0]").value(shopping));
	}

	@Test
	void 他人のラベルは削除できず404() throws Exception {
		Cookie owner = signup();
		long othersLabel = idOf(addLabel(owner, "仕事", "#3e63dd"));

		mockMvc.perform(delete("/api/labels/{id}", othersLabel).with(csrf()).cookie(signup()))
				.andExpect(status().isNotFound());
		// 持ち主のラベルは残っている
		mockMvc.perform(get("/api/board").cookie(owner))
				.andExpect(jsonPath("$.labels.length()").value(1));
	}

	@Test
	void 存在しないラベルの削除は404() throws Exception {
		mockMvc.perform(delete("/api/labels/{id}", 999_999_999L).with(csrf()).cookie(signup()))
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

	private ResultActions addLabel(Cookie session, String name, String color) throws Exception {
		return mockMvc.perform(post("/api/labels").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(labelJson(name, color)));
	}

	private ResultActions updateLabel(Cookie session, long labelId, String name, String color) throws Exception {
		return mockMvc.perform(put("/api/labels/{id}", labelId).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(labelJson(name, color)));
	}

	// 一番左のリストにカードを足して、その ID を返す
	private long addCard(Cookie session, String title) throws Exception {
		String board = mockMvc.perform(get("/api/board").cookie(session))
				.andReturn().getResponse().getContentAsString();
		long columnId = JsonPath.<Number>read(board, "$.columns[0].id").longValue();
		String json = mockMvc.perform(post("/api/columns/{id}/cards", columnId).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "title": "%s" }
						""".formatted(title)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(json, "$.id").longValue();
	}

	private static long idOf(ResultActions result) throws Exception {
		String json = result.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(json, "$.id").longValue();
	}

	private static String labelJson(String name, String color) {
		return """
				{ "name": "%s", "color": "%s" }
				""".formatted(name, color);
	}

}
