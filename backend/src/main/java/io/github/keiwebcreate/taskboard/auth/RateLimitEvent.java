package io.github.keiwebcreate.taskboard.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 回数制限の記録（rate_limit_events テーブルの1行）。ログインの失敗・ゲストの作成のたびに1行を入れる（詳細設計書 3.8.8）。
 * 一度入れたら変えないので、変更のメソッドはない。
 */
@Entity
@Table(name = "rate_limit_events")
public class RateLimitEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RateLimitKind kind;

	@Column(name = "ip_address", nullable = false)
	private String ipAddress;

	// ほかのテーブルと違い、Java の時計で入れる。数えるときの「今」（Instant.now()）と同じ時計にそろえるため
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	// JPA が DB から読み込むときに使う。アプリのコードからは呼ばない
	protected RateLimitEvent() {
	}

	public RateLimitEvent(RateLimitKind kind, String ipAddress, Instant createdAt) {
		this.kind = kind;
		this.ipAddress = ipAddress;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public RateLimitKind getKind() {
		return kind;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
