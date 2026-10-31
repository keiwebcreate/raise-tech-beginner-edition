package io.github.keiwebcreate.taskboard.board;

import io.github.keiwebcreate.taskboard.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * ボード（boards テーブルの1行）。ユーザー1人につき1つ。
 */
@Entity
@Table(name = "boards")
public class Board {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 持ち主。LAZY：必要になるまでユーザーを DB から読まない
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, length = 50)
	private String name;

	// JPA が DB から読み込むときに使う。アプリのコードからは呼ばない
	protected Board() {
	}

	public Board(User user, String name) {
		this.user = user;
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public String getName() {
		return name;
	}

}
