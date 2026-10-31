package io.github.keiwebcreate.taskboard.common.position;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 並び順の振り直しのテスト。DB を使わない、ロジックだけの単体テスト（詳細設計書 3.10）。
 */
class PositionsTest {

	// テスト用の、並び順だけを持つもの。何回番号を変えたかも数える
	static class Item implements Positioned {
		private int position;
		private int changes;

		Item(int position) {
			this.position = position;
		}

		@Override
		public int getPosition() {
			return position;
		}

		@Override
		public void changePosition(int position) {
			this.position = position;
			changes++;
		}
	}

	@Test
	void 真ん中が抜けていたら_0から詰めて付け直す() {
		// 0, 1, 2, 3 のうち 1 を削除した残り
		List<Item> items = List.of(new Item(0), new Item(2), new Item(3));

		Positions.renumber(items);

		assertThat(items).extracting(Item::getPosition).containsExactly(0, 1, 2);
	}

	@Test
	void 番号が変わらないものは変えない() {
		List<Item> items = List.of(new Item(0), new Item(1), new Item(5));

		Positions.renumber(items);

		assertThat(items).extracting(item -> item.changes).containsExactly(0, 0, 1);
	}

	@Test
	void 並べ替えたListの順番どおりに付け直す() {
		// A=0, B=1, C=2, D=3 の D を A と B の間に移す → A=0, D=1, B=2, C=3（テーブル設計書 4章の例）
		Item a = new Item(0);
		Item b = new Item(1);
		Item c = new Item(2);
		Item d = new Item(3);
		List<Item> items = new ArrayList<>(List.of(a, b, c, d));
		items.remove(d);
		items.add(1, d);

		Positions.renumber(items);

		assertThat(List.of(a, d, b, c)).extracting(Item::getPosition).containsExactly(0, 1, 2, 3);
	}

	@Test
	void 空のListでもエラーにならない() {
		Positions.renumber(List.of());
	}

}
