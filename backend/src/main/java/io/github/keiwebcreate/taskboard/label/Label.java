package io.github.keiwebcreate.taskboard.label;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * ラベル（labels テーブルの1行）。ユーザーごとに自由に作れる。
 */
@Entity
@Table(name = "labels")
public class Label {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, length = 20)
	private String name;

	// "#e5484d" のような色。テーブルでは固定長の CHAR(7) なので、@JdbcTypeCode で型を合わせる
	// （String はふつう VARCHAR とみなされ、起動時の validate で「型が違う」と止まる）
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 7)
	private String color;

	protected Label() {
	}

	public Label(User user, String name, String color) {
		this.user = user;
		this.name = name;
		this.color = color;
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

	public String getColor() {
		return color;
	}

	// A-17 ラベルの変更（名前と色）
	public void update(String name, String color) {
		this.name = name;
		this.color = color;
	}

}
