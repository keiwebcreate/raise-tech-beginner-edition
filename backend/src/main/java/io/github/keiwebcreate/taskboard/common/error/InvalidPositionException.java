package io.github.keiwebcreate.taskboard.common.error;

/**
 * 並び替えの移動先の位置が範囲の外（400 INVALID_POSITION。API設計書 A-11・A-15）。
 */
public class InvalidPositionException extends RuntimeException {

	public InvalidPositionException() {
		super("移動先の位置が正しくありません");
	}

}
