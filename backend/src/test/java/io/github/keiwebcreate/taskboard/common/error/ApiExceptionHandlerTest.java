package io.github.keiwebcreate.taskboard.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import io.github.keiwebcreate.taskboard.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;

/**
 * 想定外の入力でも 500 にならず、API設計書 4章の形（code 付き）で返ることを確かめる（イシュー #31）。
 * どれも画面からは起きないが、API を直接呼ばれたときに 500 とエラーログが出ないようにする。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApiExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	private Cookie session;

	@BeforeEach
	void signup() throws Exception {
		session = mockMvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "email": "user-%s@example.com", "password": "password123" }
						""".formatted(UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getCookie("SESSION");
		assertThat(session).as("セッションの Cookie が返っている").isNotNull();
	}

	@Test
	void URLのIDが数字でなければ400() throws Exception {
		mockMvc.perform(put("/api/cards/abc").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.detail").value("入力内容に誤りがあります"));
	}

	@Test
	void JSONが壊れていたら400() throws Exception {
		mockMvc.perform(post("/api/columns").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"name\": "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void 入力チェックの失敗は項目ごとのメッセージを返す() throws Exception {
		mockMvc.perform(post("/api/columns").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"name\": \" \" }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("name"))
				.andExpect(jsonPath("$.errors[0].message").value("リスト名を入力してください"));
	}

	@Test
	void 存在しないURLは404() throws Exception {
		mockMvc.perform(get("/api/no-such-api").cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void メソッドが違えば405で_使えるメソッドをAllowヘッダーで返す() throws Exception {
		mockMvc.perform(post("/api/board").with(csrf()).cookie(session))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
				.andExpect(header().exists("Allow"));
	}

	@Test
	void JSON以外を送ったら415() throws Exception {
		mockMvc.perform(post("/api/columns").with(csrf()).cookie(session)
				.contentType(MediaType.TEXT_PLAIN)
				.content("保留"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

}
