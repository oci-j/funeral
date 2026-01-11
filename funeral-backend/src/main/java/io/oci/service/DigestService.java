package io.oci.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DigestService {

    public String calculateDigest(
            String content
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance(
                    "SHA-256"
            );
            byte[] hash = digest.digest(
                    content.getBytes()
            );
            return "sha256:" + bytesToHex(
                    hash
            );
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(
                    "SHA-256 not available",
                    e
            );
        }
    }

    private String bytesToHex(
            byte[] bytes
    ) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(
                    String.format(
                            "%02x",
                            b
                    )
            );
        }
        return result.toString();
    }
}
