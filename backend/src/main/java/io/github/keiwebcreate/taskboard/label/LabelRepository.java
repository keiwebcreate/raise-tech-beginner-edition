package io.github.keiwebcreate.taskboard.label;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * labels テーブルとのやりとり。
 */
public interface LabelRepository extends JpaRepository<Label, Long> {

	// SELECT ... FROM labels WHERE user_id = ? ORDER BY id（ラベル一覧を作成順に）
	List<Label> findByUserIdOrderById(Long userId);

	// カードに付けるラベル。指定した ID のうち、自分のものだけが返る（詳細設計書 3.5）
	// 返った数が指定した数より少なければ、他人のラベルか存在しないラベルが混ざっている
	List<Label> findAllByIdInAndUserId(Collection<Long> labelIds, Long userId);

}
