package io.github.keiwebcreate.taskboard.auth.dto;

/**
 * A-19 退会で受け取る JSON。例：{ "password": "password123" }
 * 入力チェックは付けない。空のときも、違うときと同じ「パスワードが正しくありません」にする（API設計書 A-19）。
 */
public record WithdrawRequest(String password) {
}
