package io.github.keiwebcreate.taskboard.label;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

	// 自分のラベル1件。ID と持ち主の両方を条件にして探す（詳細設計書 3.5）
	Optional<Label> findByIdAndUserId(Long labelId, Long userId);

	// 自分のラベルの数（上限の確認に使う）
	long countByUserId(Long userId);

	// 同じ名前のラベルがあるか（追加のとき）
	boolean existsByUserIdAndName(Long userId, String name);

	// 自分以外に同じ名前のラベルがあるか（変更のとき。今の自分の名前は重複とみなさない）
	boolean existsByUserIdAndNameAndIdNot(Long userId, String name, Long labelId);

}
