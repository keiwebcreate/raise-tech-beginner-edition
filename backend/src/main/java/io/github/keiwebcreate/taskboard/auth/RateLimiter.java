package io.github.keiwebcreate.taskboard.auth;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.taskboard.common.error.RateLimitedException;

/**
 * 回数制限（要件 U-10、詳細設計書 3.8.8）。IP アドレスごとに、直近の決まった時間の記録を数える。
 *
 * check と record は、呼ぶ側（AuthService）のトランザクションの中で呼ぶ（MANDATORY：トランザクションがなければエラー）。
 * check でかけた鍵はトランザクションが終わるまで外れないので、同じ IP アドレスからの別の処理は
 * 「数える → 記録する」が終わるまで待つ。同時に送られても、数え間違えて上限を超えることがない。
 */
@Service
public class RateLimiter {

	private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

	private final RateLimitEventRepository rateLimitEventRepository;
	private final RateLimitProperties properties;

	public RateLimiter(RateLimitEventRepository rateLimitEventRepository, RateLimitProperties properties) {
		this.rateLimitEventRepository = rateLimitEventRepository;
		this.properties = properties;
	}

	/**
	 * 同じ IP アドレスに鍵をかけ、上限に達していないかを確かめる。
	 *
	 * @throws RateLimitedException 数える時間の中の記録が、上限の数に達しているとき
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void check(RateLimitKind kind, String ipAddress) {
		rateLimitEventRepository.lock(kind.name() + ":" + ipAddress);

		RateLimitProperties.Rule rule = properties.ruleOf(kind);
		Instant now = Instant.now();
		Instant since = now.minus(rule.window());
		long count = rateLimitEventRepository.countByKindAndIpAddressAndCreatedAtAfter(kind, ipAddress, since);
		if (count < rule.maxCount()) {
			return;
		}

		// 古いほうから数えて「これが外れれば上限を下回る」記録が、数える時間から外れたときに、また試せる。
		// ふつうは記録がちょうど上限の数なので、いちばん古い記録になる。
		// 上限の数を設定で下げたあとは、上限より多く残っていることがあるので、その分を飛ばす
		int skip = (int) (count - rule.maxCount());
		Instant freedAt = rateLimitEventRepository.findCreatedAtSkipping(kind, ipAddress, since, skip)
				.map(createdAt -> createdAt.plus(rule.window()))
				.orElse(now);
		throw new RateLimitedException(toSecondsRoundingUp(Duration.between(now, freedAt)));
	}

	/**
	 * 1回分を記録する。check のあとで呼ぶ。
	 * 記録して、ちょうど上限の数になったら（回数制限が働きはじめたら）、警告のログを1回だけ出す（要件 5章のログ）。
	 * 制限中に断るたびには出さない（大量に送られたときに、ログがふくらまないように）。
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(RateLimitKind kind, String ipAddress) {
		Instant now = Instant.now();
		rateLimitEventRepository.save(new RateLimitEvent(kind, ipAddress, now));

		RateLimitProperties.Rule rule = properties.ruleOf(kind);
		long count = rateLimitEventRepository.countByKindAndIpAddressAndCreatedAtAfter(kind, ipAddress,
				now.minus(rule.window()));
		if (count == rule.maxCount()) {
			// ユーザーはまだわからない（ログイン前）ので、IP アドレスを出す
			log.warn("回数制限に達しました（{}、IPアドレス {}、{}分に{}回）", kind, ipAddress, rule.window().toMinutes(),
					rule.maxCount());
		}
	}

	// 秒にする。端数は切り上げ、少なくとも1秒（0秒後に試せる、とは言わない）
	private static long toSecondsRoundingUp(Duration duration) {
		long seconds = (duration.toMillis() + 999) / 1000;
		return Math.max(1, seconds);
	}

}
