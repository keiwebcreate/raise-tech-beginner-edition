package io.github.keiwebcreate.taskboard.label;

import java.util.Arrays;

/**
 * ラベルに使える8色（画面設計書 6章）。React の constants.ts の LABEL_COLORS と同じ一覧。
 * DB には色コード（"#e5484d" など）で保存する。
 */
public enum LabelColor {

	RED("#e5484d"),
	ORANGE("#f76b15"),
	YELLOW("#ffc53d"),
	GREEN("#30a46c"),
	SKY("#0090ff"),
	BLUE("#3e63dd"),
	PURPLE("#8e4ec6"),
	GRAY("#8b8d98");

	private final String code;

	LabelColor(String code) {
		this.code = code;
	}

	public String getCode() {
		return code;
	}

	// 8色のどれかの色コードか
	public static boolean isValid(String code) {
		return Arrays.stream(values()).anyMatch(color -> color.code.equals(code));
	}

}
