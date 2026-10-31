package io.github.keiwebcreate.taskboard.column;

import io.github.keiwebcreate.taskboard.board.Board;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * リスト（board_columns テーブルの1行）。画面では「未着手」「作業中」などの列。
 * Java の @Column と名前がぶつからないように、クラス名は BoardColumn にしている。
 */
@Entity
@Table(name = "board_columns")
public class BoardColumn {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "board_id", nullable = false)
	private Board board;

	@Column(nullable = false, length = 50)
	private String name;

	// 左からの並び順（0から始まる連番）
	@Column(nullable = false)
	private int position;

	protected BoardColumn() {
	}

	public BoardColumn(Board board, String name, int position) {
		this.board = board;
		this.name = name;
		this.position = position;
	}

	public Long getId() {
		return id;
	}

	public Board getBoard() {
		return board;
	}

	public String getName() {
		return name;
	}

	public int getPosition() {
		return position;
	}

}
