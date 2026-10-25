package io.github.keiwebcreate.taskboard;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TaskBoardApplication {

	public static void main(String[] args) {
		// ログの時刻などをそろえるため、どの環境でも UTC で動かす（詳細設計書 3.7）
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(TaskBoardApplication.class, args);
	}

}
