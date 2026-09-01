package cz.inovatika.authors;

import cz.inovatika.utils.XMLUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class AuthorDataUtilsTest {

    @Test
    public void parseAuthorsData() throws ParserConfigurationException, IOException, SAXException {
        InputStream resourceAsStream = this.getClass().getResourceAsStream("mods.xml");
        Assertions.assertNotNull(resourceAsStream);
        List<AuthorData> authorData = AuthorDataUtils.parseAuthorData(XMLUtils.parseDocument(resourceAsStream, true));
        System.out.println(authorData);
    }

}
