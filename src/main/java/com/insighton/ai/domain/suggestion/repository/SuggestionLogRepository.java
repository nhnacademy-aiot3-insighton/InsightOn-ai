package com.insighton.ai.domain.suggestion.repository;

import com.insighton.ai.domain.suggestion.entity.SuggestionLog;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SuggestionLogRepository extends JpaRepository<SuggestionLog, Long> {

    @Query("""
            select s from SuggestionLog s
            where s.groupId = :groupId
            and (cast(:locationId as java.lang.Long) is null or s.locationId = :locationId)
            and (cast(:from as java.time.OffsetDateTime) is null or s.createdAt >= :from)
            and (cast(:to as java.time.OffsetDateTime) is null or s.createdAt <= :to)
            order by s.createdAt desc
            """)
    List<SuggestionLog> search(@Param("groupId") Long groupId, @Param("locationId") Long locationId,
                               @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
                               Pageable pageable);

    @Query("""
            select count(s) from SuggestionLog s
            where s.groupId = :groupId
            and (cast(:locationId as java.lang.Long) is null or s.locationId = :locationId)
            and (cast(:from as java.time.OffsetDateTime) is null or s.createdAt >= :from)
            and (cast(:to as java.time.OffsetDateTime) is null or s.createdAt <= :to)
            """)
    long count(@Param("groupId") Long groupId, @Param("locationId") Long locationId,
               @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Modifying
    @Query("delete from SuggestionLog s where s.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Modifying
    @Query("delete from SuggestionLog s where s.locationId = :locationId")
    void deleteByLocationId(@Param("locationId") Long locationId);

    @Query("""
                   select s from SuggestionLog s
                   where s.locationId = :locationId
                   and s.createdAt >= :from
                   and s.createdAt <= :to
                   order by s.createdAt desc
            """)
    List<SuggestionLog> searchByPeriod(@Param("locationId") Long locationId,
                                       @Param("from") OffsetDateTime from,
                                       @Param("to") OffsetDateTime to);

    List<SuggestionLog> findByLocationIdAndIsAcceptedNotNullOrderByCreatedAtDesc(Long locationId, Pageable pageable);

    /**
     * "대기 중일 때만 수락으로 바꾼다"는 조건부(CAS) 업데이트. 이 쿼리 자체가 원자적이라, 같은 제안에
     * 동시/중복 수락 요청이 들어와도 실제로 대기(null)→수락(true)으로 바뀌는 건 정확히 한쪽뿐이다 -
     * 영향 행 수(0 또는 1)로 "이미 처리됐는지"를 판단한다(SuggestionLogServiceImpl.accept 참고).
     */
    @Modifying
    @Query("update SuggestionLog s set s.isAccepted = true where s.suggestionLogId = :id and s.isAccepted is null")
    int acceptIfPending(@Param("id") Long suggestionLogId);
}
