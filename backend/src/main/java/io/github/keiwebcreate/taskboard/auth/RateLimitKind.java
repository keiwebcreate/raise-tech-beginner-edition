package io.github.keiwebcreate.taskboard.auth;

/**
 * 回数制限で数えるものの種類（要件 U-10）。rate_limit_events.kind に文字列で保存する。
 */
public enum RateLimitKind {

	/** ログインの失敗（A-02） */
	LOGIN_FAILURE,

	/** ゲストの作成（A-03） */
	GUEST_CREATION

}
