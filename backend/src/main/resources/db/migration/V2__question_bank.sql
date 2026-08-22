CREATE TABLE question_bank (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_text   TEXT NOT NULL,
    question_type   VARCHAR(30) NOT NULL DEFAULT 'behavioral', -- behavioral | technical | company_specific
    category        VARCHAR(50),                               -- e.g. leadership, conflict, failure, teamwork
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_question_bank_type ON question_bank(question_type);

INSERT INTO question_bank (question_text, question_type, category) VALUES
('Tell me about a time you disagreed with a teammate. How did you handle it?', 'behavioral', 'conflict'),
('Describe a project that failed or didn''t go as planned. What did you learn?', 'behavioral', 'failure'),
('Tell me about a time you had to meet a tight deadline. How did you prioritize?', 'behavioral', 'time_management'),
('Give an example of when you took initiative without being asked.', 'behavioral', 'initiative'),
('Describe a situation where you had to persuade someone to see things your way.', 'behavioral', 'influence'),
('Tell me about a time you received difficult feedback. How did you respond?', 'behavioral', 'growth'),
('Describe a time you had to work with someone whose working style was very different from yours.', 'behavioral', 'teamwork'),
('Tell me about a time you made a mistake at work. What happened and what did you do?', 'behavioral', 'accountability'),
('Describe a situation where you had to learn something new quickly to complete a task.', 'behavioral', 'adaptability'),
('Tell me about a time you led a project or initiative, even without a formal leadership title.', 'behavioral', 'leadership'),
('Give an example of a goal you set for yourself and how you achieved it.', 'behavioral', 'goal_setting'),
('Describe a time you had to say no to a request or push back on scope.', 'behavioral', 'boundaries'),
('Tell me about a time you had incomplete information but still had to make a decision.', 'behavioral', 'decision_making'),
('Describe the most challenging technical problem you''ve solved and how you approached it.', 'behavioral', 'problem_solving'),
('Tell me about a time you had to give a teammate difficult feedback.', 'behavioral', 'feedback'),
('Describe a time you had competing priorities. How did you decide what came first?', 'behavioral', 'prioritization'),
('Tell me about a time you went above and beyond what was expected of you.', 'behavioral', 'ownership'),
('Describe a time you had to explain something technical to a non-technical audience.', 'behavioral', 'communication'),
('Tell me about a time you worked on a team with unclear roles or responsibilities.', 'behavioral', 'ambiguity'),
('Why are you looking to leave your current role, or why did you leave your last one?', 'behavioral', 'motivation');
