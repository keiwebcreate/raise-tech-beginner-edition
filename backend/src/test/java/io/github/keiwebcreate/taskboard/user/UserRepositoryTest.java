package io.github.keiwebcreate.taskboard.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import io.github.keiwebcreate.taskboard.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;

/**
 * UserRepository のテスト。
 * - @DataJpaTest：JPA と DB まわりの部品だけを起動する（Web の部品は起動しないので速い）
 * - TestcontainersConfiguration：Docker で使い捨ての PostgreSQL 18 を立ち上げ、Flyway でテーブルを作る
 * - テストが終わるたびに、DB への変更は元に戻る（ロールバック）
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void 保存したユーザーをメールアドレスで探せる() {
		userRepository.saveAndFlush(new User("taro@example.com", "ハッシュ化したパスワード", false));
		// JPA が覚えている（キャッシュしている）データを捨て、次の検索で必ず DB から読み直させる
		entityManager.clear();

		Optional<User> found = userRepository.findByEmail("taro@example.com");

		assertThat(found).isPresent();
		User user = found.get();
		assertThat(user.getId()).isNotNull(); // DB が番号を振った
		assertThat(user.getEmail()).isEqualTo("taro@example.com");
		assertThat(user.getPasswordHash()).isEqualTo("ハッシュ化したパスワード");
		assertThat(user.isGuest()).isFalse();
		assertThat(user.getCreatedAt()).isNotNull(); // DB が作成日時を入れた
	}

	@Test
	void 登録されていないメールアドレスでは見つからない() {
		assertThat(userRepository.findByEmail("nobody@example.com")).isEmpty();
	}

	@Test
	void メールアドレスが登録済みかどうかを確かめられる() {
		userRepository.saveAndFlush(new User("hanako@example.com", "ハッシュ化したパスワード", false));

		assertThat(userRepository.existsByEmail("hanako@example.com")).isTrue();
		assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
	}

}
