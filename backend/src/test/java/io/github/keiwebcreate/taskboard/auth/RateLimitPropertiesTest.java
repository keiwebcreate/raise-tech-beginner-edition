package io.github.keiwebcreate.taskboard.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

import io.github.keiwebcreate.taskboard.TestcontainersConfiguration;

/**
 * 回数制限の設定（app.rate-limit）のテスト。
 * - application.yml の値が、要件 U-10 のとおりに読み込まれているか
 *   （回数制限の動き（RateLimitApiTest）は、テストのために上限を3回にして確かめている）
 * - 書き忘れや0以下の数があると、起動の時点でエラーになるか
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RateLimitPropertiesTest {

	// 設定の読み込みだけを試すための、小さなアプリ（DB などは起動しない）
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(PropertiesOnly.class);

	@Autowired
	private RateLimitProperties properties;

	@Test
	void ログインの失敗は15分に10回まで_ゲストの作成は1時間に10回まで() {
		assertThat(properties.loginFailure()).isEqualTo(new RateLimitProperties.Rule(10, Duration.ofMinutes(15)));
		assertThat(properties.guestCreation()).isEqualTo(new RateLimitProperties.Rule(10, Duration.ofHours(1)));
		// 古い記録は、いちばん長い1時間より前のものを消す
		assertThat(properties.longestWindow()).isEqualTo(Duration.ofHours(1));
	}

	@Test
	void 設定がそろっていれば起動できる() {
		runner.withPropertyValues(loginFailure("10")).withPropertyValues(guestCreation("10"))
				.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void 設定を書き忘れると_起動の時点でエラーになる() {
		runner.withPropertyValues(loginFailure("10"))
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void 回数が0だと_起動の時点でエラーになる() {
		runner.withPropertyValues(loginFailure("10")).withPropertyValues(guestCreation("0"))
				.run(context -> assertThat(context).hasFailed());
	}

	private static String[] loginFailure(String maxCount) {
		return new String[] {"app.rate-limit.login-failure.max-count=" + maxCount,
				"app.rate-limit.login-failure.window=15m"};
	}

	private static String[] guestCreation(String maxCount) {
		return new String[] {"app.rate-limit.guest-creation.max-count=" + maxCount,
				"app.rate-limit.guest-creation.window=1h"};
	}

	@EnableConfigurationProperties(RateLimitProperties.class)
	static class PropertiesOnly {
	}

}
