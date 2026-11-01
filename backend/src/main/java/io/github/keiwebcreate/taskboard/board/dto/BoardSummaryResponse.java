package io.github.keiwebcreate.taskboard.board.dto;

import io.github.keiwebcreate.taskboard.board.Board;

/**
 * ボードの ID と名前だけの JSON。例：{ "id": 1, "name": "仕事用ボード" }
 * ボード一覧（A-22）の1件、ボードの作成（A-23）とボード名の変更（A-07）の返事に使う。
 */
public record BoardSummaryResponse(Long id, String name) {

	public static BoardSummaryResponse from(Board board) {
		return new BoardSummaryResponse(board.getId(), board.getName());
	}

}
