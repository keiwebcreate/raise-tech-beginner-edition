package io.github.keiwebcreate.taskboard.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ユーザー（users テーブルの1行）。
 * テーブルは Flyway の V1 で作る。JPA はこのクラスとテーブルが合っているかを確かめて、読み書きするだけ。
 */
@Entity
@Table(name = "users")
public class User {

	// 主キー。DB が自動で番号を振る（GENERATED ALWAYS AS IDENTITY）
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "is_guest", nullable = false)
	private boolean guest;

	// 作成日時は DB が自動で入れるので、Java からは書き込まない
	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	// JPA が DB から読み込むときに使う。アプリのコードからは呼ばない
	protected User() {
	}

	public User(String email, String passwordHash, boolean guest) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.guest = guest;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public boolean isGuest() {
		return guest;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
