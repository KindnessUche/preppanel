package com.preppanel.llm.dto;

/** The panel's decision for the next turn: what kind of move, and which topic it concerns. */
public record TurnPlan(TurnType type, InterviewPlan.Topic topic) {
}
