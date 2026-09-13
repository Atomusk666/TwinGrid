package com.rgds.ultimate.shell;

import java.util.HashMap;
import java.util.Map;

/** A release cannot inherit an action from an old page, selection, editor, or display. */
final class ActionKeyLatch {
    private static final Map<String,String> pending = new HashMap<>();
    static void press(String key, String context) { pending.put(key, context); }
    static boolean release(String key, String context, boolean canceled) {
        String pressed = pending.remove(key);
        return !canceled && pressed != null && pressed.equals(context);
    }
    static void reset() { pending.clear(); }
}
