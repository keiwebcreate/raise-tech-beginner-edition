package io.github.keiwebcreate.taskboard.auth;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 古い回数制限の記録を消す（詳細設計書 3.8.8）。
 * 毎時0分に、いちばん長い数える時間（今は1時間）より古い行を消す。数えないので、残しておく意味がない。
 */
@Component
public class RateLimitCleanupJob {

	private static final Logger log = LoggerFactory.getLogger(RateLimitCleanupJob.class);

	private final RateLimitEventRepository rateLimitEventRepository;
	private final RateLimitProperties properties;

	public RateLimitCleanupJob(RateLimitEventRepository rateLimitEventRepository, RateLimitProperties properties) {
		this.rateLimitEventRepository = rateLimitEventRepository;
		this.properties = properties;
	}

	// cron は「秒 分 時 日 月 曜日」。"0 0 * * * *" は毎時0分0秒
	@Scheduled(cron = "0 0 * * * *")
	public void deleteExpiredEvents() {
		int deleted = rateLimitEventRepository.deleteCreatedBefore(Instant.now().minus(properties.longestWindow()));
		log.info("古い回数制限の記録を{}件削除しました", deleted);
	}

}
