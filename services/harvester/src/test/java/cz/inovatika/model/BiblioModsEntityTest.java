package cz.inovatika.model;

import cz.inovatika.utils.XMLUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.security.NoSuchAlgorithmException;

public class BiblioModsEntityTest {

    @Test
    public void testEntity() throws IOException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {
        InputStream resourceAsStream = this.getClass().getResourceAsStream("mods.xml");
        Assertions.assertNotNull(resourceAsStream);
        BiblioModsEntity entity = BiblioModsEntity.create(resourceAsStream);
        Assertions.assertNotNull(entity);
        Assertions.assertNotNull(entity.getBiblioMods());
        Assertions.assertNotNull(entity.getMd5Sum());

        Document document = XMLUtils.parseDocument(new StringReader(entity.getBiblioMods()), true);
        Assertions.assertNotNull(document);
    }
}
