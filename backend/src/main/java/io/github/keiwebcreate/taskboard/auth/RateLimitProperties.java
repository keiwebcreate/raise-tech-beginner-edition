package io.github.keiwebcreate.taskboard.auth;

import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 回数制限の数と時間（要件 U-10）。値は application.yml の app.rate-limit から読む。
 * 書き忘れや0以下の数があれば、起動の時点でエラーにする（@Validated）。
 * チェックがないと起動はできてしまい、最初のログインで 500 になって原因が分かりにくいため。
 *
 * @param loginFailure  ログインの失敗
 * @param guestCreation ゲストの作成
 * @param registration  ユーザー登録
 */
@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(@NotNull @Valid Rule loginFailure, @NotNull @Valid Rule guestCreation,
		@NotNull @Valid Rule registration) {

	/**
	 * 「window の間に maxCount 回まで」。
	 *
	 * @param maxCount 数える時間の中で、許す回数
	 * @param window   数える時間（application.yml では 15m・1h のように書く）
	 */
	public record Rule(@Positive int maxCount, @NotNull Duration window) {
	}

	public Rule ruleOf(RateLimitKind kind) {
		return switch (kind) {
			case LOGIN_FAILURE -> loginFailure;
			case GUEST_CREATION -> guestCreation;
			case REGISTRATION -> registration;
		};
	}

	// いちばん長い数える時間。これより古い記録は、どの回数制限でも数えないので消してよい
	public Duration longestWindow() {
		return Arrays.stream(RateLimitKind.values())
				.map(kind -> ruleOf(kind).window())
				.max(Comparator.naturalOrder())
				.orElseThrow();
	}

}
