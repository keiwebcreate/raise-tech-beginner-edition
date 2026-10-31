package io.github.keiwebcreate.taskboard.board.dto;

import io.github.keiwebcreate.taskboard.board.Board;

/**
 * A-07 ボード名の変更で返す JSON。例：{ "id": 1, "name": "仕事用ボード" }
 */
public record BoardNameResponse(Long id, String name) {

	public static BoardNameResponse from(Board board) {
		return new BoardNameResponse(board.getId(), board.getName());
	}

}
