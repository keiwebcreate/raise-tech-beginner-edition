package io.github.keiwebcreate.taskboard.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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

}
