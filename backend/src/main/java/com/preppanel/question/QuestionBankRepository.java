package com.preppanel.question;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, UUID> {

    /**
     * Picks a random active question of the given type that hasn't already been
     * used (by question_bank_id) in the given session. Random ordering is fine
     * at this table size (tens/hundreds of rows) - revisit with a smarter
     * sampling strategy if the bank grows large.
     */
    @Query(value = """
            SELECT qb.* FROM question_bank qb
            WHERE qb.active = true
              AND qb.question_type = :questionType
              AND qb.id NOT IN (
                  SELECT q.question_bank_id FROM questions q
                  WHERE q.session_id = :sessionId AND q.question_bank_id IS NOT NULL
              )
            ORDER BY random()
            LIMIT 1
            """, nativeQuery = true)
    QuestionBank findRandomUnusedForSession(@Param("sessionId") UUID sessionId,
                                             @Param("questionType") String questionType);

    List<QuestionBank> findByQuestionTypeAndActiveTrue(String questionType);
}
