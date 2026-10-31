package io.github.keiwebcreate.taskboard.card;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.github.keiwebcreate.taskboard.column.BoardColumn;
import io.github.keiwebcreate.taskboard.label.Label;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * カード（cards テーブルの1行）。
 */
@Entity
@Table(name = "cards")
public class Card {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// カードが入っているリスト
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "column_id", nullable = false)
	private BoardColumn column;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(length = 2000)
	private String description;

	// enum を "HIGH" などの文字列として保存する（数字で保存すると、並びを変えたときに意味が変わってしまう）
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Level importance;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Level urgency;

	@Column(name = "due_date")
	private LocalDate dueDate;

	// リストの中での上からの並び順（0から始まる連番）
	@Column(nullable = false)
	private int position;

	// 付いているラベル。中間テーブル card_labels でつながる
	@ManyToMany
	@JoinTable(name = "card_labels",
			joinColumns = @JoinColumn(name = "card_id"),
			inverseJoinColumns = @JoinColumn(name = "label_id"))
	private Set<Label> labels = new HashSet<>();

	protected Card() {
	}

	// カードを追加するとき（A-12）はタイトルだけ。重要度・緊急度は「中」から始める
	public Card(BoardColumn column, String title, int position) {
		this.column = column;
		this.title = title;
		this.importance = Level.MEDIUM;
		this.urgency = Level.MEDIUM;
		this.position = position;
	}

	public Long getId() {
		return id;
	}

	public BoardColumn getColumn() {
		return column;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public Level getImportance() {
		return importance;
	}

	public Level getUrgency() {
		return urgency;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public int getPosition() {
		return position;
	}

	// 外から勝手に足したり消したりできないよう、読むだけの形で返す
	public Set<Label> getLabels() {
		return Collections.unmodifiableSet(labels);
	}

	/**
	 * カード詳細の保存（A-13）。所属するリストと並び順は変えない。
	 * ラベルは、渡された一覧でまるごと置き換える（中間テーブル card_labels の行は JPA が足し引きする）。
	 */
	public void update(String title, String description, Level importance, Level urgency, LocalDate dueDate,
			Set<Label> labels) {
		this.title = title;
		this.description = description;
		this.importance = importance;
		this.urgency = urgency;
		this.dueDate = dueDate;
		this.labels.clear();
		this.labels.addAll(labels);
	}

}
