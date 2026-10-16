package br.com.pdflocal.model;

public enum OfficeKind {
    WORD("WINWORD.EXE", "Word.Application"),
    EXCEL("EXCEL.EXE", "Excel.Application"),
    POWERPOINT("POWERPNT.EXE", "PowerPoint.Application");

    private final String executable;
    private final String comName;

    OfficeKind(String executable, String comName) {
        this.executable = executable;
        this.comName = comName;
    }

    public String executable() {
        return executable;
    }

    public String comName() {
        return comName;
    }
}
