package io.github.keiwebcreate.taskboard.user;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;

/**
 * users テーブルとのやりとり。
 * JpaRepository を継承するだけで、保存（save）・削除（delete）などが使える。
 * メソッド名の決まりに沿って書くと、Spring Data JPA が SQL を自動で作る。
 */
public interface UserRepository extends JpaRepository<User, Long> {

	// SELECT ... FROM users WHERE email = ?（ログインで使う）
	Optional<User> findByEmail(String email);

	// 同じメールアドレスが登録済みか（ユーザー登録で使う）
	boolean existsByEmail(String email);

	/**
	 * SELECT ... FROM users WHERE id = ? FOR UPDATE（ユーザーの行に鍵をかけて取る。詳細設計書 3.8.2）
	 * ボードの作成（A-23）で、数えてから作るまでの間に、別のタブからボードを作られないようにする。
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<User> findWithLockById(Long id);

	/**
	 * DELETE FROM users WHERE id = ? AND is_guest = TRUE（ゲストのログアウトで使う。要件 U-08）
	 * ゲストでないユーザーは消さない。ボード・リスト・カード・ラベルは、DB の ON DELETE CASCADE で一緒に消える。
	 *
	 * @return 消した行の数（0 か 1）
	 */
	@Modifying
	@Transactional
	@Query("delete from User u where u.id = :userId and u.guest = true")
	int deleteGuestById(Long userId);

	/**
	 * DELETE FROM users WHERE is_guest = TRUE AND created_at < ?（古いゲストの削除。テーブル設計書 5章）
	 * インデックス (is_guest, created_at) で、対象をすばやく探せる。
	 *
	 * @return 消した行の数
	 */
	@Modifying
	@Transactional
	@Query("delete from User u where u.guest = true and u.createdAt < :time")
	int deleteGuestsCreatedBefore(Instant time);

}
