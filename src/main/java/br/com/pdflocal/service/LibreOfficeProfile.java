package br.com.pdflocal.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class LibreOfficeProfile {

    private static final String SEED_RESOURCE = "/scripts/libreoffice-profile.xcu";
    private static final String SEED_NAME = "registrymodifications.xcu";

    private LibreOfficeProfile() {
    }

    static Path defaultHome() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null || local.isBlank() ? Path.of(System.getProperty("java.io.tmpdir")) : Path.of(local);
        return base.resolve("PdfLocal").resolve("lo");
    }

    static Path ensure(Path home) throws IOException {
        Path profile = home.resolve("p");
        Path user = Files.createDirectories(profile.resolve("user"));
        Path seed = user.resolve(SEED_NAME);
        if (!Files.exists(seed)) {
            try (InputStream in = LibreOfficeProfile.class.getResourceAsStream(SEED_RESOURCE)) {
                if (in == null) {
                    throw new IOException("Missing profile seed");
                }
                Files.copy(in, seed, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return profile;
    }

    static String uri(Path profile) {
        String uri = profile.toUri().toString();
        return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }
}
