package io.github.keiwebcreate.taskboard.label;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.keiwebcreate.taskboard.auth.LoginUser;
import io.github.keiwebcreate.taskboard.label.dto.LabelRequest;
import io.github.keiwebcreate.taskboard.label.dto.LabelResponse;
import jakarta.validation.Valid;

/**
 * ラベルの API（API設計書 5.5）。ラベルの一覧は A-06（ボードの取得）に入っているので、一覧の API はない。
 */
@RestController
@RequestMapping("/api/labels")
public class LabelController {

	private final LabelService labelService;

	public LabelController(LabelService labelService) {
		this.labelService = labelService;
	}

	// A-16 ラベルの追加
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public LabelResponse add(@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody LabelRequest request) {
		return labelService.add(loginUser.getId(), request);
	}

	// A-17 ラベルの変更（名前と色をまとめて置き換えるので PUT）
	@PutMapping("/{labelId}")
	public LabelResponse update(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long labelId,
			@Valid @RequestBody LabelRequest request) {
		return labelService.update(loginUser.getId(), labelId, request);
	}

	// A-18 ラベルの削除。成功したら 204（中身なし）
	@DeleteMapping("/{labelId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long labelId) {
		labelService.delete(loginUser.getId(), labelId);
	}

}
