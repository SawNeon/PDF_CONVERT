package br.com.pdflocal.service;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

public final class ProtectStep implements OutputStep {

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 64;

    private static final int ENCRYPTION_KEY_BITS = 256;
    private static final int OWNER_PASSWORD_LENGTH = 32;
    private static final String OWNER_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final char[] password;

    public ProtectStep(char[] password) {
        Objects.requireNonNull(password, "password");
        if (password.length < MIN_PASSWORD_LENGTH || password.length > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Invalid password length");
        }
        this.password = password.clone();
    }

    @Override
    public void apply(PDDocument document, SaveListener listener) throws IOException {
        try {
            AccessPermission permissions = new AccessPermission();
            permissions.setCanModify(false);
            permissions.setCanModifyAnnotations(false);
            permissions.setCanAssembleDocument(false);
            StandardProtectionPolicy policy = new StandardProtectionPolicy(randomOwnerPassword(),
                    new String(password), permissions);
            policy.setEncryptionKeyLength(ENCRYPTION_KEY_BITS);
            policy.setPreferAES(true);
            document.protect(policy);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static String randomOwnerPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder owner = new StringBuilder(OWNER_PASSWORD_LENGTH);
        for (int i = 0; i < OWNER_PASSWORD_LENGTH; i++) {
            owner.append(OWNER_ALPHABET.charAt(random.nextInt(OWNER_ALPHABET.length())));
        }
        return owner.toString();
    }
}
