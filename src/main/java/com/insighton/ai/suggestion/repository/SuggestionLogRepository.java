package com.insighton.ai.suggestion.repository;

import com.insighton.ai.suggestion.domain.SuggestionLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
