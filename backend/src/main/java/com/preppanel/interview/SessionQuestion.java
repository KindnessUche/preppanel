package com.preppanel.interview;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionQuestion {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "question_bank_id")
    private UUID questionBankId;

    @Column(name = "sequence_num", nullable = false)
    private int sequenceNum;

    @Column(name = "question_text", nullable = false)
    private String questionText;

    @Column(name = "question_type", nullable = false)
    private String questionType;

    @Column(nullable = false)
    private String source; // bank | llm_generated | company_research

    @Column(name = "panelist_archetype_id")
    private String panelistArchetypeId;

    @Column(name = "panelist_name")
    private String panelistName;

    @Column(name = "reaction_text", columnDefinition = "text")
    private String reactionText; // this panelist's in-character reaction to the PREVIOUS answer

    @Column(name = "mood_shift")
    private String moodShift; // positive | neutral | negative
}
