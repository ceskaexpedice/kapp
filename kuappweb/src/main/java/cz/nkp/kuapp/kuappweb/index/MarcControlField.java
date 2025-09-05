package cz.nkp.kuapp.kuappweb.index;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.util.ArrayList;
import java.util.List;
import org.apache.solr.client.solrj.beans.Field;

/**
 *
 * @author alberto
 */
@JacksonXmlRootElement(localName = "record")
public class MarcControlField  {

    @JacksonXmlProperty(localName = "leader")
    @Field
    public String leader;
    
    @JacksonXmlProperty(localName = "tag", isAttribute = true)
    public List<String> tag  = new ArrayList<String>();

}
