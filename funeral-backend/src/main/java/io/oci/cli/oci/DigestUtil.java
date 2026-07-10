package io.oci.cli.oci;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class DigestUtil {

    private DigestUtil() {
    }

    public static String sha256(
            byte[] bytes
    ) {
        try {
            MessageDigest md = MessageDigest.getInstance(
                    "SHA-256"
            );
            return "sha256:" + HexFormat.of()
                    .formatHex(
                            md.digest(
                                    bytes
                            )
                    );
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(
                    e
            );
        }
    }
}
