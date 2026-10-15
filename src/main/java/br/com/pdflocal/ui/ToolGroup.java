package br.com.pdflocal.ui;

enum ToolGroup {
    OPTIMIZE("tools.group.optimize"),
    CONVERT_TO_PDF("tools.group.convert-to"),
    CONVERT_FROM_PDF("tools.group.convert-from"),
    EDIT("tools.group.edit"),
    SECURITY("tools.group.security");

    private final String messageKey;

    ToolGroup(String messageKey) {
        this.messageKey = messageKey;
    }

    String messageKey() {
        return messageKey;
    }
}
