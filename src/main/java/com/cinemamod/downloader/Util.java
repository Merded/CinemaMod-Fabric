package com.cinemamod.downloader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

public final class Util {
    public static String sha1Hash(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");

        String var12;
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];

            for(int len = input.read(buffer); len != -1; len = input.read(buffer)) {
                md.update(buffer, 0, len);
            }

            StringBuilder result = new StringBuilder();

            for(byte b : md.digest()) {
                result.append(String.format("%02x", b));
            }

            var12 = result.toString();
        }

        return var12;
    }

    public static void makeExecNix(File file) {
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);

        try {
            Files.setPosixFilePermissions(file.toPath(), perms);
        } catch (IOException var3) {
        }
    }
}
