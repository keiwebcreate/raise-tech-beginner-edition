package io.github.keiwebcreate.taskboard.common.error;

/**
 * 同じ名前のラベルがすでにある（409 LABEL_NAME_DUPLICATED。要件 T-01）。
 */
public class LabelNameDuplicatedException extends RuntimeException {

	public LabelNameDuplicatedException() {
		super("同じ名前のラベルがあります");
	}

}
