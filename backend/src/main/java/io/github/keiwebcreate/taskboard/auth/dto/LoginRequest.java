package io.github.keiwebcreate.taskboard.auth.dto;

/**
 * A-02 ログインで受け取る JSON。例：{ "email": "taro@example.com", "password": "password123" }
 * 入力チェックは付けない。空などの場合も、ログイン失敗（401 LOGIN_FAILED）にそろえる（API設計書 A-02）。
 */
public record LoginRequest(String email, String password) {
}
