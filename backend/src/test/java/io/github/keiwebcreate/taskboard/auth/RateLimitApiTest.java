package io.github.keiwebcreate.taskboard.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.github.keiwebcreate.taskboard.TestcontainersConfiguration;

/**
 * 回数制限のテスト（要件 U-10、詳細設計書 3.8.8）。
 * ほかのテストはリクエストごとに違う IP アドレスになる（TestcontainersConfiguration）ので、
 * ここではテストごとに決めた IP アドレスを付けて送る。
 *
 * 上限はこのテストだけ3回にする。本番の10回だと、DB の接続の数（同時に10本）と同じになり、
 * 「同時に送る」テストで、鍵がなくても偶然うまくいってしまうため（接続を待つ間に、前の処理が終わる）。
 * 本番の値が読み込まれていることは RateLimitPropertiesTest で確かめる。
 */
@SpringBootTest(properties = {
		"app.rate-limit.login-failure.max-count=3",
		"app.rate-limit.guest-creation.max-count=3" })
@AutoConfigureMockMvc
// ログに出た文字を、テストの中で読めるようにする（CapturedOutput）
@ExtendWith(OutputCaptureExtension.class)
@Import(TestcontainersConfiguration.class)
class RateLimitApiTest {

	private static final String PASSWORD = "password123";
	private static final String WRONG_PASSWORD = "wrong-password";
	private static final int MAX_COUNT = 3;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private RateLimitCleanupJob rateLimitCleanupJob;

	// ===== A-02 ログイン =====

	@Test
	void 上限の回数まで失敗すると_次は正しいパスワードでも429になる() throws Exception {
		String ip = newIpAddress();
		String email = signup();
		failLogins(ip, email, MAX_COUNT);

		MvcResult result = login(ip, email, PASSWORD)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"))
				.andExpect(jsonPath("$.detail").value("しばらくしてから再度お試しください"))
				.andReturn();
		// 何秒後に試せるか（15分 = 900秒以内）
		assertThat(retryAfter(result)).isBetween(1L, 900L);
	}

	@Test
	void 回数制限はIPアドレスごとに数える() throws Exception {
		String email = signup();
		failLogins(newIpAddress(), email, MAX_COUNT);

		login(newIpAddress(), email, PASSWORD).andExpect(status().isOk());
	}

	@Test
	void ログインに成功しても_失敗の回数は消えない() throws Exception {
		String ip = newIpAddress();
		String email = signup();
		failLogins(ip, email, MAX_COUNT - 1);
		login(ip, email, PASSWORD).andExpect(status().isOk());
		failLogins(ip, email, 1);

		login(ip, email, PASSWORD).andExpect(status().isTooManyRequests());
	}

	@Test
	void 制限中の失敗は数えない() throws Exception {
		String ip = newIpAddress();
		String email = signup();
		failLogins(ip, email, MAX_COUNT);
		login(ip, email, WRONG_PASSWORD).andExpect(status().isTooManyRequests());

		assertThat(countEvents(ip)).isEqualTo(MAX_COUNT);
	}

	@Test
	void 十五分より前の失敗は数えない() throws Exception {
		String ip = newIpAddress();
		String email = signup();
		failLogins(ip, email, MAX_COUNT);
		moveEventsBack(ip, Duration.ofMinutes(16));

		login(ip, email, PASSWORD).andExpect(status().isOk());
	}

	@Test
	void RetryAfterは_いちばん古い失敗が15分を過ぎるまでの秒数() throws Exception {
		String ip = newIpAddress();
		String email = signup();
		failLogins(ip, email, MAX_COUNT);
		// 10分前の失敗にする → あと5分（300秒）で1回分あく
		moveEventsBack(ip, Duration.ofMinutes(10));

		MvcResult result = login(ip, email, PASSWORD)
				.andExpect(status().isTooManyRequests())
				.andReturn();
		// テストを動かしている間に数秒たつので、少し幅を持たせる
		assertThat(retryAfter(result)).isBetween(290L, 300L);
	}

	@Test
	void 上限より多く記録が残っていたら_RetryAfterはその分も待つ() throws Exception {
		String ip = newIpAddress();
		String email = signup();
		// 上限の数を設定で下げたあとのように、上限（3回）より多い5回分が残っている（14〜10分前）
		for (int minutes = 14; minutes >= 10; minutes--) {
			jdbcTemplate.update(
					"INSERT INTO rate_limit_events (kind, ip_address, created_at) VALUES ('LOGIN_FAILURE', ?, ?)", ip,
					Instant.now().minus(Duration.ofMinutes(minutes)).atOffset(ZoneOffset.UTC));
		}

		MvcResult result = login(ip, email, PASSWORD)
				.andExpect(status().isTooManyRequests())
				.andReturn();
		// 古いほうから3件目（12分前）が外れると残りが2回になって試せる → あと3分（180秒）
		assertThat(retryAfter(result)).isBetween(170L, 180L);
	}

	@Test
	void 上限に達したときだけ_ログを1回出す(CapturedOutput output) throws Exception {
		String ip = newIpAddress();
		String email = signup();
		failLogins(ip, email, MAX_COUNT);
		// 制限中に断ったときは出さない
		login(ip, email, WRONG_PASSWORD).andExpect(status().isTooManyRequests());
		login(ip, email, PASSWORD).andExpect(status().isTooManyRequests());

		String message = "回数制限に達しました（LOGIN_FAILURE、IPアドレス " + ip + "、";
		assertThat(output.getOut().split(Pattern.quote(message), -1)).hasSize(2);
	}

	@Test
	void 同時にログインに失敗しても_上限を超えない() throws Exception {
		String ip = newIpAddress();
		String email = signup();

		List<Integer> statuses = sendAtOnce(MAX_COUNT + 5, () -> loginRequest(ip, email, WRONG_PASSWORD));

		// 鍵がないと、全部が「まだ0回」と数えて 401 になる（鍵を外して、このテストが失敗することを確かめた）
		assertThat(Collections.frequency(statuses, 401)).isEqualTo(MAX_COUNT);
		assertThat(Collections.frequency(statuses, 429)).isEqualTo(5);
		assertThat(countEvents(ip)).isEqualTo(MAX_COUNT);
	}

	// ===== A-03 ゲストログイン =====

	@Test
	void ゲストは上限の回数まで作れる() throws Exception {
		String ip = newIpAddress();
		for (int i = 0; i < MAX_COUNT; i++) {
			mockMvc.perform(guestRequest(ip)).andExpect(status().isCreated());
		}

		MvcResult result = mockMvc.perform(guestRequest(ip))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"))
				.andExpect(jsonPath("$.detail").value("しばらくしてから再度お試しください"))
				.andReturn();
		assertThat(retryAfter(result)).isBetween(1L, 3600L);
		// ほかの IP アドレスからは作れる
		mockMvc.perform(guestRequest(newIpAddress())).andExpect(status().isCreated());
	}

	@Test
	void 同時にゲストを作っても_上限を超えない() throws Exception {
		String ip = newIpAddress();

		List<Integer> statuses = sendAtOnce(MAX_COUNT + 5, () -> guestRequest(ip));

		assertThat(Collections.frequency(statuses, 201)).isEqualTo(MAX_COUNT);
		assertThat(Collections.frequency(statuses, 429)).isEqualTo(5);
	}

	// ===== 古い記録の削除 =====

	@Test
	void 一時間より古い記録だけが消える() throws Exception {
		String oldIp = newIpAddress();
		String newIp = newIpAddress();
		mockMvc.perform(guestRequest(oldIp)).andExpect(status().isCreated());
		mockMvc.perform(guestRequest(newIp)).andExpect(status().isCreated());
		moveEventsBack(oldIp, Duration.ofMinutes(61));
		moveEventsBack(newIp, Duration.ofMinutes(59));

		rateLimitCleanupJob.deleteExpiredEvents();

		assertThat(countEvents(oldIp)).isZero();
		assertThat(countEvents(newIp)).isOne();
	}

	// ===== 手助けのメソッド =====

	// テストごとの IP アドレス。ほかのテストの 2001:db8:: と重ならないように 2001:db8:1:: を使う
	private static String newIpAddress() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return "2001:db8:1::%x:%x".formatted(random.nextInt(0x10000), random.nextInt(0x10000));
	}

	// リクエストの接続元の IP アドレスを ip にする
	private static RequestPostProcessor from(String ip) {
		return request -> {
			request.setRemoteAddr(ip);
			return request;
		};
	}

	private String signup() throws Exception {
		String email = "user-" + UUID.randomUUID() + "@example.com";
		mockMvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(email, PASSWORD)))
				.andExpect(status().isCreated());
		return email;
	}

	private static MockHttpServletRequestBuilder loginRequest(String ip, String email, String password) {
		return post("/api/auth/login").with(csrf()).with(from(ip))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(email, password));
	}

	private ResultActions login(String ip, String email, String password) throws Exception {
		return mockMvc.perform(loginRequest(ip, email, password));
	}

	private void failLogins(String ip, String email, int times) throws Exception {
		for (int i = 0; i < times; i++) {
			login(ip, email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}
	}

	private static MockHttpServletRequestBuilder guestRequest(String ip) {
		return post("/api/auth/guest").with(csrf()).with(from(ip));
	}

	// times 回のリクエストを、別々のスレッドから同時に送り、ステータスを返す
	private List<Integer> sendAtOnce(int times, Supplier<MockHttpServletRequestBuilder> request) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(times);
		try {
			// 全部のスレッドの準備ができてから、いっせいに送る
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Integer>> responses = new ArrayList<>();
			for (int i = 0; i < times; i++) {
				responses.add(executor.submit(() -> {
					start.await();
					return mockMvc.perform(request.get()).andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> response : responses) {
				statuses.add(response.get(30, TimeUnit.SECONDS));
			}
			return statuses;
		} finally {
			executor.shutdownNow();
		}
	}

	private static long retryAfter(MvcResult result) {
		String header = result.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
		assertThat(header).as("Retry-After が付いている").isNotNull();
		return Long.parseLong(header);
	}

	// その IP アドレスの記録を、すべて「今から ago 前」にする（時間がたったことにする）
	private void moveEventsBack(String ip, Duration ago) {
		jdbcTemplate.update("UPDATE rate_limit_events SET created_at = ? WHERE ip_address = ?",
				Instant.now().minus(ago).atOffset(ZoneOffset.UTC), ip);
	}

	private long countEvents(String ip) {
		Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM rate_limit_events WHERE ip_address = ?",
				Long.class, ip);
		return count == null ? 0 : count;
	}

	private static String json(String email, String password) {
		return """
				{ "email": "%s", "password": "%s" }
				""".formatted(email, password);
	}

}
