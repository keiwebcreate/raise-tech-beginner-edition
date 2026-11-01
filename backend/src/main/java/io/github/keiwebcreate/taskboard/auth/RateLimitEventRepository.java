package io.github.keiwebcreate.taskboard.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * rate_limit_events テーブルとのやりとり（詳細設計書 3.8.8）。
 */
public interface RateLimitEventRepository extends JpaRepository<RateLimitEvent, Long> {

	/**
	 * SELECT pg_advisory_xact_lock(hashtext(?))（名前に鍵をかける。PostgreSQL のアドバイザリーロック）
	 * 鍵をかける行がまだないので、「種類:IPアドレス」という名前に鍵をかける。トランザクションが終わると外れる。
	 * 同じ名前に鍵をかけようとした別の処理は、外れるまで待つ。
	 * 関数の戻り値は void（値なし）なので、FROM に置いて 1 を返させる。
	 */
	@Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtext(:key))", nativeQuery = true)
	int lock(String key);

	// SELECT count(*) FROM rate_limit_events WHERE kind = ? AND ip_address = ? AND created_at > ?
	long countByKindAndIpAddressAndCreatedAtAfter(RateLimitKind kind, String ipAddress, Instant since);

	/**
	 * 数えている時間の中の記録を古い順に並べ、skip 件を飛ばした次の記録の日時（あと何秒で試せるかを出すため）。
	 * 例：上限3回で記録が5件なら、古いほうから3件目（skip = 2）が外れたときに、残りが2件になって試せる。
	 */
	@Query("""
			select e.createdAt from RateLimitEvent e
			where e.kind = :kind and e.ipAddress = :ipAddress and e.createdAt > :since
			order by e.createdAt
			limit 1 offset :skip
			""")
	Optional<Instant> findCreatedAtSkipping(RateLimitKind kind, String ipAddress, Instant since, int skip);

	/**
	 * DELETE FROM rate_limit_events WHERE created_at < ?（古い記録の削除）
	 *
	 * @return 消した行の数
	 */
	@Modifying
	@Transactional
	@Query("delete from RateLimitEvent e where e.createdAt < :time")
	int deleteCreatedBefore(Instant time);

}
