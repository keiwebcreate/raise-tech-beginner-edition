package io.github.keiwebcreate.taskboard.common.error;

/**
 * ゲストにはできない操作をしようとした（403 GUEST_NOT_ALLOWED）。
 * 退会（A-19）、メールアドレスの変更（A-20）、パスワードの変更（A-21）。
 * 画面ではゲストにこれらのメニューを出さないので、ふつうは起きない。
 */
public class GuestNotAllowedException extends RuntimeException {

	public GuestNotAllowedException(String message) {
		super(message);
	}

}
