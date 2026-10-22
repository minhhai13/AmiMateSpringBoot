package com.minhhai.amimate.repository;
import com.minhhai.amimate.dto.response.ChatSessionResponse;
import com.minhhai.amimate.entity.ChatLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ChatLogRepository extends JpaRepository<ChatLog, Long> {
    Page<ChatLog> findByDeviceId(String deviceId, Pageable pageable);
    Page<ChatLog> findByDeviceIdAndSessionId(String deviceId, String sessionId, Pageable pageable);

    @Query(value = """
            select new com.minhhai.amimate.dto.response.ChatSessionResponse(
                c.sessionId, max(c.createdAt), count(c))
            from ChatLog c
            where c.deviceId = :deviceId
            group by c.sessionId
            order by max(c.createdAt) desc
            """,
            countQuery = """
                    select count(distinct c.sessionId)
                    from ChatLog c
                    where c.deviceId = :deviceId
                    """)
    Page<ChatSessionResponse> findSessionsByDeviceId(
            @Param("deviceId") String deviceId,
            Pageable pageable);

    Optional<ChatLog> findByDeviceIdAndClientMessageId(String deviceId, String clientMessageId);
}