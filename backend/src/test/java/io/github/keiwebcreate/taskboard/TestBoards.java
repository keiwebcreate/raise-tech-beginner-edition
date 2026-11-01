package io.github.keiwebcreate.taskboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * テストの手助け：ボード一覧（A-22）から、いちばん古いボード（登録したときにできたボード）を探す。
 * 1人が複数のボードを持てるようになり、ボードの API に ID が要るようになったため（イシュー #51）。
 */
public final class TestBoards {

	private TestBoards() {
	}

	// いちばん古いボードの ID
	public static long firstBoardId(MockMvc mockMvc, Cookie session) throws Exception {
		String json = mockMvc.perform(get("/api/boards").cookie(session))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(json, "$[0].id").longValue();
	}

	// いちばん古いボードの URL（A-06 ボードの取得・A-07 ボード名の変更）
	public static String firstBoardPath(MockMvc mockMvc, Cookie session) throws Exception {
		return "/api/boards/" + firstBoardId(mockMvc, session);
	}

}
