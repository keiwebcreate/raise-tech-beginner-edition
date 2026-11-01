package io.github.keiwebcreate.taskboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * すべてのテストで使う設定。DB（Testcontainers の PostgreSQL）と、MockMvc の初期設定。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
	}

	/**
	 * MockMvc のリクエストを、毎回違う IP アドレスから来たことにする。
	 * 何もしないとすべて 127.0.0.1 になり、ほかのテストのログイン失敗・ゲスト作成と合わせて数えられて、
	 * 回数制限（要件 U-10）にかかってしまう。回数制限のテスト（RateLimitApiTest）は、決まった IP アドレスを付けて送る。
	 */
	@Bean
	MockMvcBuilderCustomizer randomRemoteAddress() {
		return builder -> builder.defaultRequest(get("/").with(request -> {
			request.setRemoteAddr(randomIpAddress());
			return request;
		}));
	}

	// 2001:db8:: は、説明やテストのために取ってある IPv6 アドレス（インターネットには出ない）
	private static String randomIpAddress() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return "2001:db8::%x:%x".formatted(random.nextInt(0x10000), random.nextInt(0x10000));
	}

}
