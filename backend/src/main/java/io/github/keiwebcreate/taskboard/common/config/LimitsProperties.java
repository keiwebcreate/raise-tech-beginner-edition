package io.github.keiwebcreate.taskboard.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * データ量の上限（要件 3.7）。値は application.yml の app.limits から読む（設定で変えられるようにするため）。
 *
 * @param columns        1ボードのリストの数
 * @param cardsPerColumn 1リストのカードの数
 * @param labels         1ユーザーのラベルの数
 */
@ConfigurationProperties("app.limits")
public record LimitsProperties(int columns, int cardsPerColumn, int labels) {
}
