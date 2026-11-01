package io.github.keiwebcreate.taskboard.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import io.github.keiwebcreate.taskboard.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;

/**
 * API のアクセスのログ（要件 NO-03・NO-04）と、API の返事のセキュリティのヘッダー（要件 NS-10）のテスト。
 */
@SpringBootTest
@AutoConfigureMockMvc
// ログに出た文字を、テストの中で読めるようにする（CapturedOutput）
@ExtendWith(OutputCaptureExtension.class)
@Import(TestcontainersConfiguration.class)
class AccessLogFilterTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void ログインしていないアクセスも_メソッド_URL_ステータス_処理時間を残す(CapturedOutput output) throws Exception {
		mockMvc.perform(get("/api/boards")).andExpect(status().isUnauthorized());

		assertThat(output.getOut()).containsPattern(Pattern.quote("GET /api/boards 401 ") + "\\d+ms（未ログイン）");
	}

	@Test
	void ログイン中はユーザーIDを残し_クエリ文字列とメールアドレスは残さない(CapturedOutput output) throws Exception {
		String email = "user-" + UUID.randomUUID() + "@example.com";
		MvcResult signup = mockMvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "email": "%s", "password": "password123" }
						""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		Cookie session = signup.getResponse().getCookie("SESSION");

		mockMvc.perform(get("/api/boards").queryParam("q", "ひみつの言葉").cookie(session))
				.andExpect(status().isOk());

		assertThat(output.getOut())
				// 登録の API は、登録したあとのユーザー ID で残る
				.containsPattern(Pattern.quote("POST /api/auth/signup 201 ") + "\\d+ms（ユーザーID \\d+）")
				.containsPattern(Pattern.quote("GET /api/boards 200 ") + "\\d+ms（ユーザーID \\d+）")
				.doesNotContain("ひみつの言葉")
				.doesNotContain(email);
	}

	@Test
	void APIの返事には_何も読み込ませず埋め込ませないヘッダーが付く() throws Exception {
		mockMvc.perform(get("/api/boards"))
				.andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
				// Spring Security が初めから付けているもの
				.andExpect(header().string("X-Frame-Options", "DENY"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

}
