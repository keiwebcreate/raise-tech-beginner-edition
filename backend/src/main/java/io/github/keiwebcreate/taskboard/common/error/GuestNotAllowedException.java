package io.github.keiwebcreate.taskboard.common.error;

/**
 * ゲストにはできない操作をしようとした（403 GUEST_NOT_ALLOWED）。今は退会（A-19）だけ。
 * 画面ではゲストに「退会する」を出さないので、ふつうは起きない。
 */
public class GuestNotAllowedException extends RuntimeException {

	public GuestNotAllowedException() {
		super("ゲストは退会できません");
	}

}
