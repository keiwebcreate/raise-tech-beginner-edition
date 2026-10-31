package io.github.keiwebcreate.taskboard;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
// @ConfigurationProperties の付いたクラス（LimitsProperties など）を探して、application.yml の値を読み込ませる
@ConfigurationPropertiesScan
public class TaskBoardApplication {

	public static void main(String[] args) {
		// ログの時刻などをそろえるため、どの環境でも UTC で動かす（詳細設計書 3.7）
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(TaskBoardApplication.class, args);
	}

}
