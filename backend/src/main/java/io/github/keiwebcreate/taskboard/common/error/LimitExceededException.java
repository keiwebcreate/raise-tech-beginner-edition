package io.github.keiwebcreate.taskboard.common.error;

/**
 * データ量の上限を超えた（409 LIMIT_EXCEEDED。要件 3.7）。
 * メッセージは画面にそのまま出す（例：「リストは20個までです」）。
 */
public class LimitExceededException extends RuntimeException {

	public LimitExceededException(String message) {
		super(message);
	}

}
