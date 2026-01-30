package com.jokerhub.paper.plugin.orzmc.infra.templates;

public final class ExceptionFormatter {
    private ExceptionFormatter() {}

    public static String summarize(Throwable t) {
        if (t == null) return "unknown";
        String type = t.getClass().getSimpleName();
        String msg = t.getMessage();
        String head = type + (msg != null && !msg.isEmpty() ? (": " + msg) : "");
        StackTraceElement[] st = t.getStackTrace();
        if (st != null && st.length > 0) {
            StackTraceElement e = st[0];
            return head + " at " + e.getClassName() + "." + e.getMethodName() + "(" + e.getFileName() + ":"
                    + e.getLineNumber() + ")";
        }
        return head;
    }
}
