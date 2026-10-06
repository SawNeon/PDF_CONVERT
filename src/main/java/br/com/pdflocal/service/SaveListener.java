package br.com.pdflocal.service;

public interface SaveListener {

    SaveListener NONE = new SaveListener() {
        @Override
        public void onPage(int done, int total) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    void onPage(int done, int total);

    boolean isCancelled();
}
