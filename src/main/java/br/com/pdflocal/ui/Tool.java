package br.com.pdflocal.ui;

import java.util.Objects;

record Tool(ToolGroup group, String titleKey, Runnable action, boolean requiresPages) {

    Tool {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(titleKey, "titleKey");
        Objects.requireNonNull(action, "action");
    }
}
