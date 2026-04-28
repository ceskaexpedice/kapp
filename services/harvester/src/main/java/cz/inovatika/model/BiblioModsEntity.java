package cz.inovatika.model;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

public record BiblioModsEntity(String biblioMods, String md5Sum) {

    public static final Logger LOGGER = Logger.getLogger(BiblioModsEntity.class.getName());

    public static BiblioModsEntity create(InputStream inputStream) throws IOException, NoSuchAlgorithmException {
        try (inputStream) {
            byte[] byteArray = IOUtils.toByteArray(inputStream);
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(byteArray);
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            String md5String = sb.toString();
            return new BiblioModsEntity(new String(byteArray, StandardCharsets.UTF_8), md5String);
        }
    }
}
