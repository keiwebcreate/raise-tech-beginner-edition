package io.github.keiwebcreate.taskboard.common.position;

/**
 * 並び順（position）を持つもの（リスト・カード）。Positions.renumber で番号を振り直すために使う。
 */
public interface Positioned {

	int getPosition();

	// 並び順だけを変える（Positions から呼ぶ。setter の代わり）
	void changePosition(int position);

}
