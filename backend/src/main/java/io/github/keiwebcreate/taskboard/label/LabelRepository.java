package io.github.keiwebcreate.taskboard.label;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * labels テーブルとのやりとり。
 */
public interface LabelRepository extends JpaRepository<Label, Long> {

	// SELECT ... FROM labels WHERE user_id = ? ORDER BY id（ラベル一覧を作成順に）
	List<Label> findByUserIdOrderById(Long userId);

}
