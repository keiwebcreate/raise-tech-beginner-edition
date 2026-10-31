package io.github.keiwebcreate.taskboard.column;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.keiwebcreate.taskboard.auth.LoginUser;
import io.github.keiwebcreate.taskboard.column.dto.ColumnRequest;
import io.github.keiwebcreate.taskboard.column.dto.ColumnResponse;
import jakarta.validation.Valid;

/**
 * リストの API（API設計書 5.3）。
 */
@RestController
@RequestMapping("/api/columns")
public class ColumnController {

	private final ColumnService columnService;

	public ColumnController(ColumnService columnService) {
		this.columnService = columnService;
	}

	// A-08 リストの追加
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ColumnResponse add(@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody ColumnRequest request) {
		return columnService.add(loginUser.getId(), request);
	}

}
