package io.github.keiwebcreate.taskboard.auth;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.keiwebcreate.taskboard.user.UserRepository;

/**
 * ログアウトせずに離れたゲストのデータを消す（要件 U-08、詳細設計書 3.8.6）。
 * 毎時0分に、作ってから24時間たったゲストを消す。起動クラスの @EnableScheduling で動く。
 */
@Component
public class GuestCleanupJob {

	private static final Logger log = LoggerFactory.getLogger(GuestCleanupJob.class);

	// ゲストのデータを残しておく時間
	static final Duration GUEST_LIFETIME = Duration.ofHours(24);

	private final UserRepository userRepository;

	public GuestCleanupJob(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	// cron は「秒 分 時 日 月 曜日」。"0 0 * * * *" は毎時0分0秒
	@Scheduled(cron = "0 0 * * * *")
	public void deleteExpiredGuests() {
		int deleted = userRepository.deleteGuestsCreatedBefore(Instant.now().minus(GUEST_LIFETIME));
		// 件数だけを出す（メールアドレスなどは出さない。要件 5.2）
		log.info("24時間たったゲストを{}件削除しました", deleted);
	}

}
