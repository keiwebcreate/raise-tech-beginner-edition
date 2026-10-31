package io.github.keiwebcreate.taskboard.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import io.github.keiwebcreate.taskboard.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;

/**
 * 並び順や数を変える処理が、ボードに鍵をかけてから動くことを確かめる（詳細設計書 3.8.2）。
 * 本当に同時に送ると結果がタイミングしだいになるので、テストの側で先にボードの鍵を持っておき、
 * その間に送った API が「待たされる」こと、鍵を放すと最後まで動くことを確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BoardLockTest {

	// 待たされているかを見る時間。鍵がなければ、この間に API は終わっている
	private static final long WAIT_MILLIS = 700;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void カードの並び替えは_ボードの鍵が放されるまで待つ() throws Exception {
		Cookie session = signup();
		long columnId = columnId(session, 0);
		long cardId = addCard(session, columnId, "A");
		addCard(session, columnId, "B");

		assertWaitsForLock(session, put("/api/cards/{id}/position", cardId).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"columnId\": %d, \"position\": 1 }".formatted(columnId)), 204);
	}

	@Test
	void リストの並び替えは_ボードの鍵が放されるまで待つ() throws Exception {
		Cookie session = signup();

		assertWaitsForLock(session, put("/api/columns/{id}/position", columnId(session, 0)).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"position\": 2 }"), 204);
	}

	@Test
	void カードとリストの追加と削除も_ボードの鍵が放されるまで待つ() throws Exception {
		Cookie session = signup();
		long columnId = columnId(session, 0);
		long cardId = addCard(session, columnId, "A");

		assertWaitsForLock(session, post("/api/columns/{id}/cards", columnId).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"title\": \"B\" }"), 201);
		assertWaitsForLock(session, delete("/api/cards/{id}", cardId).with(csrf()).cookie(session), 204);
		assertWaitsForLock(session, post("/api/columns").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"name\": \"保留\" }"), 201);
		assertWaitsForLock(session, delete("/api/columns/{id}", columnId).with(csrf()).cookie(session), 204);
	}

	// テストの側でボードの鍵を持っている間に request を送り、待たされることと、放したあとに expectedStatus で終わることを確かめる
	private void assertWaitsForLock(Cookie session, RequestBuilder request, int expectedStatus) throws Exception {
		long boardId = boardId(session);
		CompletableFuture<Integer> response = new TransactionTemplate(transactionManager).execute(tx -> {
			jdbcTemplate.queryForList("SELECT id FROM boards WHERE id = ? FOR UPDATE", Long.class, boardId);
			// API は別のスレッド（別のトランザクション）から送る
			CompletableFuture<Integer> sent = CompletableFuture.supplyAsync(() -> {
				try {
					return mockMvc.perform(request).andReturn().getResponse().getStatus();
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			});
			try {
				TimeUnit.MILLISECONDS.sleep(WAIT_MILLIS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			assertThat(sent).as("鍵を持っている間は終わらない").isNotDone();
			return sent;
		});
		// ここでトランザクションが終わり、鍵が放される
		assertThat(response).isNotNull();
		assertThat(response.get(10, TimeUnit.SECONDS)).isEqualTo(expectedStatus);
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

	private long boardId(Cookie session) throws Exception {
		String json = mockMvc.perform(get("/api/board").cookie(session))
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

}
