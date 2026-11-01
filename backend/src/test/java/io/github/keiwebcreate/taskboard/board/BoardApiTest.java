package io.github.keiwebcreate.taskboard.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import io.github.keiwebcreate.taskboard.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;

/**
 * 複数ボードのテスト（イシュー #51）。A-22 一覧・A-23 作成・A-24 削除と、ボードの ID を受け取る API の持ち主の確認。
 * 上限のテストのために、このテストだけボードの上限を3個にしている（登録したときに1つできるので、あと2つ）。
 */
@SpringBootTest(properties = "app.limits.boards=3")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BoardApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// ===== A-22 ボードの一覧 =====

	@Test
	void 登録するとボードが1つあり_作るたびに作った順で後ろに並ぶ() throws Exception {
		Cookie session = signup();
		createBoard(session, "仕事").andExpect(status().isCreated());
		createBoard(session, "家事").andExpect(status().isCreated());

		mockMvc.perform(get("/api/boards").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].name").value("マイボード"))
				.andExpect(jsonPath("$[1].name").value("仕事"))
				.andExpect(jsonPath("$[2].name").value("家事"))
				.andExpect(jsonPath("$[0].id").isNumber())
				// 一覧には ID と名前だけ。リストやカードは入れない
				.andExpect(jsonPath("$[0].columns").doesNotExist());
	}

	@Test
	void 一覧には他人のボードが出ない() throws Exception {
		Cookie taro = signup();
		Cookie hanako = signup();
		createBoard(hanako, "はなこの仕事").andExpect(status().isCreated());

		mockMvc.perform(get("/api/boards").cookie(taro))
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void ログインしていないと一覧を見られない() throws Exception {
		mockMvc.perform(get("/api/boards"))
				.andExpect(status().isUnauthorized());
	}

	// ===== A-23 ボードの作成 =====

	@Test
	void 作ったボードには3つのリストが入っている() throws Exception {
		Cookie session = signup();

		String json = createBoard(session, "  仕事  ")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("仕事")) // 前後の空白は取り除く
				.andReturn().getResponse().getContentAsString();
		long boardId = JsonPath.<Number>read(json, "$.id").longValue();

		mockMvc.perform(get("/api/boards/{id}", boardId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("仕事"))
				.andExpect(jsonPath("$.columns.length()").value(3))
				.andExpect(jsonPath("$.columns[0].name").value("未着手"))
				.andExpect(jsonPath("$.columns[1].name").value("作業中"))
				.andExpect(jsonPath("$.columns[2].name").value("完了"))
				.andExpect(jsonPath("$.columns[0].cards.length()").value(0));
	}

	@Test
	void ボード名が空や51文字以上なら400() throws Exception {
		Cookie session = signup();

		createBoard(session, "   ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("name"))
				.andExpect(jsonPath("$.errors[0].message").value("ボード名を入力してください"));
		createBoard(session, "あ".repeat(51))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].message").value("ボード名は50文字以内で入力してください"));
	}

	@Test
	void ボードが上限に達していたら409() throws Exception {
		Cookie session = signup();
		createBoard(session, "2つ目").andExpect(status().isCreated());
		createBoard(session, "3つ目").andExpect(status().isCreated());

		createBoard(session, "4つ目")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"))
				.andExpect(jsonPath("$.detail").value("ボードは3個までです"));
	}

	@Test
	void ボードの上限は人ごとに数える() throws Exception {
		Cookie taro = signup();
		createBoard(taro, "2つ目").andExpect(status().isCreated());
		createBoard(taro, "3つ目").andExpect(status().isCreated());

		Cookie hanako = signup();
		createBoard(hanako, "はなこの2つ目").andExpect(status().isCreated());
	}

	// ===== A-06 取得・A-07 名前の変更（ボードの ID で指定する） =====

	@Test
	void ボードごとに別のリストとカードを持つ() throws Exception {
		Cookie session = signup();
		long first = boardId(session, 0);
		long second = createdBoardId(session, "仕事");
		addCard(session, columnId(session, second, 0), "仕事のカード");

		mockMvc.perform(get("/api/boards/{id}", second).cookie(session))
				.andExpect(jsonPath("$.columns[0].cards[0].title").value("仕事のカード"));
		mockMvc.perform(get("/api/boards/{id}", first).cookie(session))
				.andExpect(jsonPath("$.columns[0].cards.length()").value(0));
	}

	@Test
	void ラベルはどのボードでも同じものが返る() throws Exception {
		Cookie session = signup();
		long first = boardId(session, 0);
		long second = createdBoardId(session, "仕事");
		mockMvc.perform(post("/api/labels").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "急ぎ", "color": "#e5484d" }
						"""))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/boards/{id}", first).cookie(session))
				.andExpect(jsonPath("$.labels[0].name").value("急ぎ"));
		mockMvc.perform(get("/api/boards/{id}", second).cookie(session))
				.andExpect(jsonPath("$.labels[0].name").value("急ぎ"));
	}

	@Test
	void ボード名を変えると_そのボードだけが変わる() throws Exception {
		Cookie session = signup();
		long first = boardId(session, 0);
		long second = createdBoardId(session, "仕事");

		mockMvc.perform(patch("/api/boards/{id}", second).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "仕事用" }
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(second))
				.andExpect(jsonPath("$.name").value("仕事用"));

		mockMvc.perform(get("/api/boards/{id}", first).cookie(session))
				.andExpect(jsonPath("$.name").value("マイボード"));
	}

	@Test
	void 他人のボードは取得も変更もできず404() throws Exception {
		Cookie taro = signup();
		Cookie hanako = signup();
		long taroBoard = boardId(taro, 0);

		mockMvc.perform(get("/api/boards/{id}", taroBoard).cookie(hanako))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(patch("/api/boards/{id}", taroBoard).with(csrf()).cookie(hanako)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "のっとり" }
						"""))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/boards/{id}", taroBoard).with(csrf()).cookie(hanako))
				.andExpect(status().isNotFound());

		// たろうのボードはそのまま
		mockMvc.perform(get("/api/boards/{id}", taroBoard).cookie(taro))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("マイボード"));
	}

	@Test
	void 存在しないボードは404() throws Exception {
		Cookie session = signup();

		mockMvc.perform(get("/api/boards/{id}", 999_999_999L).cookie(session))
				.andExpect(status().isNotFound());
	}

	// ===== A-08 リストの追加（ボードの ID で指定する） =====

	@Test
	void リストは指定したボードに追加される() throws Exception {
		Cookie session = signup();
		long first = boardId(session, 0);
		long second = createdBoardId(session, "仕事");

		mockMvc.perform(post("/api/boards/{id}/columns", second).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "保留" }
						"""))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/boards/{id}", second).cookie(session))
				.andExpect(jsonPath("$.columns.length()").value(4))
				.andExpect(jsonPath("$.columns[3].name").value("保留"));
		mockMvc.perform(get("/api/boards/{id}", first).cookie(session))
				.andExpect(jsonPath("$.columns.length()").value(3));
	}

	@Test
	void 他人のボードにはリストを追加できず404() throws Exception {
		Cookie taro = signup();
		Cookie hanako = signup();
		long taroBoard = boardId(taro, 0);

		mockMvc.perform(post("/api/boards/{id}/columns", taroBoard).with(csrf()).cookie(hanako)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "のぞき見" }
						"""))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/boards/{id}", taroBoard).cookie(taro))
				.andExpect(jsonPath("$.columns.length()").value(3));
	}

	// ===== A-15 カードの移動（同じボードの中だけ） =====

	@Test
	void カードは自分の別のボードのリストへは移せず404() throws Exception {
		Cookie session = signup();
		long first = boardId(session, 0);
		long second = createdBoardId(session, "仕事");
		long cardId = addCard(session, columnId(session, first, 0), "動かすカード");
		long otherBoardColumn = columnId(session, second, 0);

		mockMvc.perform(put("/api/cards/{id}/position", cardId).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"columnId\": %d, \"position\": 0 }".formatted(otherBoardColumn)))
				.andExpect(status().isNotFound());

		// カードは元のリストに残り、別のボードには入っていない
		mockMvc.perform(get("/api/boards/{id}", first).cookie(session))
				.andExpect(jsonPath("$.columns[0].cards[0].title").value("動かすカード"));
		mockMvc.perform(get("/api/boards/{id}", second).cookie(session))
				.andExpect(jsonPath("$.columns[0].cards.length()").value(0));
	}

	// ===== A-24 ボードの削除 =====

	@Test
	void ボードを消すと_中のリストとカードも消え_ラベルは残る() throws Exception {
		Cookie session = signup();
		long boardId = createdBoardId(session, "消すボード");
		addCard(session, columnId(session, boardId, 0), "消えるカード");
		mockMvc.perform(post("/api/labels").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "残るラベル", "color": "#e5484d" }
						"""))
				.andExpect(status().isCreated());

		mockMvc.perform(delete("/api/boards/{id}", boardId).with(csrf()).cookie(session))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/boards/{id}", boardId).cookie(session))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/boards").cookie(session))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("マイボード"));
		assertThat(count("SELECT count(*) FROM board_columns WHERE board_id = ?", boardId)).isZero();
		mockMvc.perform(get("/api/boards/{id}", boardId(session, 0)).cookie(session))
				.andExpect(jsonPath("$.labels[0].name").value("残るラベル"));
	}

	@Test
	void 最後の1つのボードも消せて_一覧は空になる() throws Exception {
		Cookie session = signup();

		mockMvc.perform(delete("/api/boards/{id}", boardId(session, 0)).with(csrf()).cookie(session))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/boards").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		// 0個からまた作れる
		createBoard(session, "新しいボード").andExpect(status().isCreated());
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

	private ResultActions createBoard(Cookie session, String name) throws Exception {
		return mockMvc.perform(post("/api/boards").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "%s" }
						""".formatted(name)));
	}

	private long createdBoardId(Cookie session, String name) throws Exception {
		String json = createBoard(session, name)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(json, "$.id").longValue();
	}

	// 一覧の index 番目（0から。作った順）のボードの ID
	private long boardId(Cookie session, int index) throws Exception {
		String json = mockMvc.perform(get("/api/boards").cookie(session))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(json, "$[" + index + "].id").longValue();
	}

	// ボードの左から index 番目（0から）のリストの ID
	private long columnId(Cookie session, long boardId, int index) throws Exception {
		String json = mockMvc.perform(get("/api/boards/{id}", boardId).cookie(session))
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

	private long count(String sql, Object... args) {
		Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
		return count == null ? 0 : count;
	}

}
