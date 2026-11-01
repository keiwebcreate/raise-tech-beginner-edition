package io.github.keiwebcreate.taskboard.common.error;

/**
 * 回数制限を超えた（429 RATE_LIMITED。要件 U-10）。
 * 何秒後に再度試せるかを持ち、ApiExceptionHandler がヘッダー Retry-After で返す（API設計書 4.3）。
 */
public class RateLimitedException extends RuntimeException {

	private final long retryAfterSeconds;

	public RateLimitedException(long retryAfterSeconds) {
		super("しばらくしてから再度お試しください");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

}
