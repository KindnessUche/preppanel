package com.preppanel.panel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PanelistArchetype.java documents that it "mirrors lib/casting.ts on the
 * frontend - keep these two in sync" but nothing previously enforced that.
 * This test pins the expected id set so a future edit to one side without
 * the other fails CI instead of silently drifting (a bad id would otherwise
 * fall back to "hiring-manager" with no error - see PanelistArchetype#byId).
 *
 * If you intentionally add/remove/rename an archetype: update BOTH this set
 * and frontend/lib/casting.ts's ARCHETYPES array in the same change.
 */
class PanelistArchetypeSyncTest {

    private static final java.util.Set<String> EXPECTED_IDS = java.util.Set.of(
            "hiring-manager", "tech-lead", "skeptic", "bar-raiser", "peer"
    );

    @Test
    void registeredArchetypeIds_matchWhatTheFrontendCastingFileExpects() {
        assertThat(PanelistArchetype.ids()).isEqualTo(EXPECTED_IDS);
    }

    @Test
    void byId_unknownId_fallsBackToHiringManager_ratherThanThrowing() {
        // Documents the existing defensive-fallback behavior noted in the v3
        // critique: a bad archetype id from a buggy frontend build shouldn't 500.
        PanelistArchetype fallback = PanelistArchetype.byId("does-not-exist");
        assertThat(fallback.id()).isEqualTo("hiring-manager");
    }
}
