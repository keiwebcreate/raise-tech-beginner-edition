package io.github.keiwebcreate.taskboard.label.dto;

import io.github.keiwebcreate.taskboard.label.Label;

/**
 * API で返すラベル。例：{ "id": 5, "name": "仕事", "color": "#e5484d" }
 */
public record LabelResponse(Long id, String name, String color) {

	public static LabelResponse from(Label label) {
		return new LabelResponse(label.getId(), label.getName(), label.getColor());
	}

}
