package io.github.keiwebcreate.taskboard.label;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import io.github.keiwebcreate.taskboard.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;

/**
 * 同じ名前のラベルがほぼ同時に追加・変更されたときに、500 ではなく 409 になることを確かめる。
 * 本当に同時に送るとタイミングしだいで結果が変わるので、「同じ名前があるか」の確認が
 * いつも「ない」と答えるようにして、確認と保存の間に割り込まれた状態を作る（SignupRaceTest と同じ考え方）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LabelRaceTest {

	@Autowired
	private MockMvc mockMvc;

	// 本物の LabelRepository を使いつつ、重複の確認の答えだけを差し替える
	@MockitoSpyBean
	private LabelRepository labelRepository;

	@Test
	void 確認と保存の間に同じ名前で追加されたら409() throws Exception {
		doReturn(false).when(labelRepository).existsByUserIdAndName(anyLong(), anyString());
		Cookie session = signup();
		addLabel(session, "仕事").andExpect(status().isCreated());

		// 2つめは確認をすり抜けるが、DB の一意制約で止まる
		addLabel(session, "仕事")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LABEL_NAME_DUPLICATED"));
	}

	@Test
	void 確認と保存の間に同じ名前に変更されたら409() throws Exception {
		doReturn(false).when(labelRepository).existsByUserIdAndNameAndIdNot(anyLong(), anyString(), any());
		Cookie session = signup();
		addLabel(session, "仕事").andExpect(status().isCreated());
		String json = addLabel(session, "買い物").andReturn().getResponse().getContentAsString();
		long labelId = JsonPath.<Number>read(json, "$.id").longValue();

		mockMvc.perform(put("/api/labels/{id}", labelId).with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"name\": \"仕事\", \"color\": \"#3e63dd\" }"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LABEL_NAME_DUPLICATED"));
	}

	private Cookie signup() throws Exception {
		return mockMvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "email": "user-%s@example.com", "password": "password123" }
						""".formatted(UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getCookie("SESSION");
	}

	private ResultActions addLabel(Cookie session, String name) throws Exception {
		return mockMvc.perform(post("/api/labels").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"name\": \"%s\", \"color\": \"#3e63dd\" }".formatted(name)));
	}

}
