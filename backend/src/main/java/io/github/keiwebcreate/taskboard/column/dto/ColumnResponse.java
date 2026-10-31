package io.github.keiwebcreate.taskboard.column.dto;

import io.github.keiwebcreate.taskboard.column.BoardColumn;

/**
 * API で返すリスト（中のカードは含まない）。例：{ "id": 13, "name": "保留" }
 */
public record ColumnResponse(Long id, String name) {

	public static ColumnResponse from(BoardColumn column) {
		return new ColumnResponse(column.getId(), column.getName());
	}

}
