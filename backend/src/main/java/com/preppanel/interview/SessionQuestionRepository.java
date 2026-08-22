package com.preppanel.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionQuestionRepository extends JpaRepository<SessionQuestion, UUID> {

    List<SessionQuestion> findBySessionIdOrderBySequenceNumAsc(UUID sessionId);

    int countBySessionId(UUID sessionId);

    Optional<SessionQuestion> findBySessionIdAndSequenceNum(UUID sessionId, int sequenceNum);
}
