package io.github.keiwebcreate.taskboard.board;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.keiwebcreate.taskboard.auth.LoginUser;
import io.github.keiwebcreate.taskboard.board.dto.BoardNameRequest;
import io.github.keiwebcreate.taskboard.board.dto.BoardResponse;
import io.github.keiwebcreate.taskboard.board.dto.BoardSummaryResponse;
import jakarta.validation.Valid;

/**
 * ボードの API（API設計書 5.2）。
 * ボードの ID は URL で受け取り、ログイン中のユーザーのボードの中だけで探す（他人のボードは 404）。
 */
@RestController
@RequestMapping("/api/boards")
public class BoardController {

	private final BoardService boardService;

	public BoardController(BoardService boardService) {
		this.boardService = boardService;
	}

	// A-22 ボードの一覧
	@GetMapping
	public List<BoardSummaryResponse> list(@AuthenticationPrincipal LoginUser loginUser) {
		return boardService.list(loginUser.getId());
	}

	// A-23 ボードの作成。成功したら 201
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public BoardSummaryResponse create(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody BoardNameRequest request) {
		return boardService.create(loginUser.getId(), request);
	}

	// A-06 ボードの取得
	@GetMapping("/{boardId}")
	public BoardResponse getBoard(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long boardId) {
		return boardService.getBoard(loginUser.getId(), boardId);
	}

	// A-07 ボード名の変更（名前だけを変えるので PATCH）
	@PatchMapping("/{boardId}")
	public BoardSummaryResponse rename(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long boardId,
			@Valid @RequestBody BoardNameRequest request) {
		return boardService.rename(loginUser.getId(), boardId, request);
	}

	// A-24 ボードの削除。成功したら 204（中身なし）
	@DeleteMapping("/{boardId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long boardId) {
		boardService.delete(loginUser.getId(), boardId);
	}

}
