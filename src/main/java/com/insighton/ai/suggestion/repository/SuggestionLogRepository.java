package com.insighton.ai.suggestion.repository;

import com.insighton.ai.suggestion.domain.SuggestionLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SuggestionLogRepository extends JpaRepository<SuggestionLog, Long> {

    @Query("""
            select s from SuggestionLog s
            where s.groupId = :groupId
            and (:locationId is null or s.locationId = :locationId)
            order by s.createdAt desc
            """)
    List<SuggestionLog> search(@Param("groupId") Long groupId, @Param("locationId") Long locationId);

    @Modifying
    @Query("delete from SuggestionLog s where s.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Modifying
    @Query("delete from SuggestionLog s where s.locationId = :locationId")
    void deleteByLocationId(@Param("locationId") Long locationId);
}
