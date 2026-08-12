package com.insighton.ai.domain.suggestion.repository;

import com.insighton.ai.domain.suggestion.entity.SuggestionLog;
import java.time.OffsetDateTime;
import java.util.List;
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
}
