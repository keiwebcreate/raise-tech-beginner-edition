package io.github.keiwebcreate.taskboard.common.position;

import java.util.List;

/**
 * 並び順の振り直し（テーブル設計書 4章、詳細設計書 3.8.2）。リストとカードで使い回す。
 * 削除やドラッグ＆ドロップのあとに、並べたい順の List を渡すと、先頭から 0, 1, 2 … と付け直す。
 */
public final class Positions {

	private Positions() {
	}

	public static void renumber(List<? extends Positioned> items) {
		for (int i = 0; i < items.size(); i++) {
			Positioned item = items.get(i);
			// 番号が変わるものだけを変える（JPA は、変わった行だけを UPDATE する）
			if (item.getPosition() != i) {
				item.changePosition(i);
			}
		}
	}

}
