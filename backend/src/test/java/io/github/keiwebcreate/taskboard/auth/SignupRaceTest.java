package io.github.keiwebcreate.taskboard.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.keiwebcreate.taskboard.TestcontainersConfiguration;
import io.github.keiwebcreate.taskboard.user.UserRepository;

/**
 * 同じメールアドレスでほぼ同時に登録されたときに、500 ではなく 409 になることを確かめる（イシュー #31）。
 * 本当に同時に送るとタイミングしだいで結果が変わるので、「登録済みか」の確認が
 * いつも「まだない」と答えるようにして、確認と保存の間に割り込まれた状態を作る。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SignupRaceTest {

	@Autowired
	private MockMvc mockMvc;

	// 本物の UserRepository を使いつつ、existsByEmail の答えだけを差し替える
	@MockitoSpyBean
	private UserRepository userRepository;

	@Test
	void 確認と保存の間に同じメールアドレスで登録されたら409() throws Exception {
		doReturn(false).when(userRepository).existsByEmail(anyString());
		String body = """
				{ "email": "user-%s@example.com", "password": "password123" }
				""".formatted(UUID.randomUUID());

		mockMvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());

		// 2人目は確認をすり抜けるが、DB の一意制約で止まる
		mockMvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"));
	}

}
