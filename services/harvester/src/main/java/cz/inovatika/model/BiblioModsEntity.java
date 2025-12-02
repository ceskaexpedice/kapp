package cz.inovatika.model;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

public class BiblioModsEntity {

    public static final Logger  LOGGER = Logger.getLogger(BiblioModsEntity.class.getName());

    private String biblioMods;
    private String md5Sum;

    private BiblioModsEntity(String biblioMods, String md5Sum) {
        this.biblioMods = biblioMods;
        this.md5Sum = md5Sum;
    }

    public String getBiblioMods() {
        return biblioMods;
    }

    public String getMd5Sum() {
        return md5Sum;
    }

    public static BiblioModsEntity create(InputStream inputStream) throws IOException, NoSuchAlgorithmException {
        byte[] byteArray = IOUtils.toByteArray(inputStream);
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(byteArray);
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        String md5String = sb.toString();
        return  new BiblioModsEntity(new String(byteArray, StandardCharsets.UTF_8), md5String);
    }
}
