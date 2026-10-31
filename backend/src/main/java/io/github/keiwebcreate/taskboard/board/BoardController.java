package io.github.keiwebcreate.taskboard.board;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.keiwebcreate.taskboard.auth.LoginUser;
import io.github.keiwebcreate.taskboard.board.dto.BoardResponse;

/**
 * ボードの API（API設計書 5.2）。
 * ボードの ID は受け取らない。ログイン中のユーザーの ID で、自分のボードだけを探す（API設計書 1章）。
 */
@RestController
@RequestMapping("/api/board")
public class BoardController {

	private final BoardService boardService;

	public BoardController(BoardService boardService) {
		this.boardService = boardService;
	}

	// A-06 ボードの取得
	@GetMapping
	public BoardResponse getBoard(@AuthenticationPrincipal LoginUser loginUser) {
		return boardService.getBoard(loginUser.getId());
	}

}
