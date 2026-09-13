package com.rgds.ultimate.shell;

/** One fail-closed rule for actions on an actual, current game row. No Android dependency. */
final class GameActionPolicy {
    static boolean allows(String page, int homeIndex, boolean ready, boolean storage,
            boolean overview, boolean folder, boolean hasGame, boolean currentMember,
            boolean editing, boolean composing, boolean busy, boolean modal) {
        return ready && storage && hasGame && currentMember && !overview && !folder
                && !editing && !composing && !busy && !modal
                && ("LIBRARY".equals(page) || "HOME".equals(page) && homeIndex == 0);
    }
}
