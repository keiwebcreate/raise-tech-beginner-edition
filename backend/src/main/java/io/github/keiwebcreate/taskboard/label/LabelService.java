package io.github.keiwebcreate.taskboard.label;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.taskboard.common.config.LimitsProperties;
import io.github.keiwebcreate.taskboard.common.error.FieldValidationException;
import io.github.keiwebcreate.taskboard.common.error.LabelNameDuplicatedException;
import io.github.keiwebcreate.taskboard.common.error.LimitExceededException;
import io.github.keiwebcreate.taskboard.common.error.NotFoundException;
import io.github.keiwebcreate.taskboard.label.dto.LabelRequest;
import io.github.keiwebcreate.taskboard.label.dto.LabelResponse;
import io.github.keiwebcreate.taskboard.user.UserRepository;

/**
 * ラベルの処理（要件 T-01・T-03）。
 */
@Service
public class LabelService {

	private final LabelRepository labelRepository;
	private final UserRepository userRepository;
	private final LimitsProperties limits;

	public LabelService(LabelRepository labelRepository, UserRepository userRepository, LimitsProperties limits) {
		this.labelRepository = labelRepository;
		this.userRepository = userRepository;
		this.limits = limits;
	}

	/**
	 * A-16 ラベルの追加。
	 *
	 * @throws FieldValidationException     色が8色のどれでもないとき
	 * @throws LabelNameDuplicatedException 同じ名前のラベルがあるとき
	 * @throws LimitExceededException       ラベルが上限（30個）に達しているとき
	 */
	@Transactional
	public LabelResponse add(Long userId, LabelRequest request) {
		String name = request.name().strip();
		checkColor(request.color());
		if (labelRepository.existsByUserIdAndName(userId, name)) {
			throw new LabelNameDuplicatedException();
		}
		if (labelRepository.countByUserId(userId) >= limits.labels()) {
			throw new LimitExceededException("ラベルは" + limits.labels() + "個までです");
		}

		// getReferenceById：ユーザーを DB から読まずに、ID だけを持った「参照」を作る（user_id を入れるだけなので）
		Label label = new Label(userRepository.getReferenceById(userId), name, request.color());
		try {
			return LabelResponse.from(labelRepository.saveAndFlush(label));
		} catch (DataIntegrityViolationException e) {
			// 上の確認と保存の間に、同じ名前で追加された（ほぼ同時の操作）。DB の一意制約（uk_labels_user_name）が止める
			throw new LabelNameDuplicatedException();
		}
	}

	/**
	 * A-17 ラベルの変更（名前と色）。今の自分の名前のままなら、重複とはみなさない。
	 *
	 * @throws NotFoundException            他人のラベル・存在しないラベルのとき
	 * @throws FieldValidationException     色が8色のどれでもないとき
	 * @throws LabelNameDuplicatedException ほかのラベルと同じ名前のとき
	 */
	@Transactional
	public LabelResponse update(Long userId, Long labelId, LabelRequest request) {
		Label label = labelRepository.findByIdAndUserId(labelId, userId)
				.orElseThrow(NotFoundException::new);
		String name = request.name().strip();
		checkColor(request.color());
		if (labelRepository.existsByUserIdAndNameAndIdNot(userId, name, labelId)) {
			throw new LabelNameDuplicatedException();
		}

		label.update(name, request.color());
		try {
			// ふつうはメソッドの終わりに UPDATE されるが、一意制約のエラーをここで受け取るために、すぐ書き込む
			labelRepository.flush();
		} catch (DataIntegrityViolationException e) {
			throw new LabelNameDuplicatedException();
		}
		return LabelResponse.from(label);
	}

	/**
	 * A-18 ラベルの削除。付いていたカードからは、DB の ON DELETE CASCADE（card_labels）で外れる（要件 T-03）。
	 *
	 * @throws NotFoundException 他人のラベル・存在しないラベルのとき
	 */
	@Transactional
	public void delete(Long userId, Long labelId) {
		Label label = labelRepository.findByIdAndUserId(labelId, userId)
				.orElseThrow(NotFoundException::new);
		labelRepository.delete(label);
	}

	private static void checkColor(String color) {
		if (!LabelColor.isValid(color)) {
			throw new FieldValidationException("color", "色を選んでください");
		}
	}

}
