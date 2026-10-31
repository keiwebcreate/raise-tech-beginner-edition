package io.github.keiwebcreate.taskboard.common.error;

/**
 * 登録済みのメールアドレスで登録しようとした（409 EMAIL_DUPLICATED）。
 */
public class EmailDuplicatedException extends RuntimeException {

	public EmailDuplicatedException() {
		super("このメールアドレスは登録済みです");
	}

}
