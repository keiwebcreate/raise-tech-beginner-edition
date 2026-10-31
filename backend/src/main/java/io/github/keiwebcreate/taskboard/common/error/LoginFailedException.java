package io.github.keiwebcreate.taskboard.common.error;

/**
 * ログインに失敗した（401 LOGIN_FAILED）。
 * メールアドレスとパスワードのどちらが違うかは教えない（要件 U-03）。
 */
public class LoginFailedException extends RuntimeException {

	public LoginFailedException() {
		super("メールアドレスまたはパスワードが正しくありません");
	}

}
