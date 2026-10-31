package io.github.keiwebcreate.taskboard.common.error;

/**
 * データが見つからない（404 NOT_FOUND）。
 * 他人のデータも、存在しないデータも、同じこの例外にする（他人のデータがあることを知られないため）。
 * Problem Details への変換は ApiExceptionHandler で行う（Controller を作るときに追加する）。
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException() {
		super("データが見つかりません");
	}

}
