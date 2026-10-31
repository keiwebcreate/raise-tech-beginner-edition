package io.github.keiwebcreate.taskboard.common.error;

/**
 * Bean Validation（@NotBlank など）では表せない入力のエラー（400 VALIDATION_ERROR。詳細設計書 3.6）。
 * 例：8色以外の色。@Valid の失敗と同じく、errors に項目ごとのメッセージを入れて返す。
 */
public class FieldValidationException extends RuntimeException {

	private final String field;

	public FieldValidationException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String getField() {
		return field;
	}

}
