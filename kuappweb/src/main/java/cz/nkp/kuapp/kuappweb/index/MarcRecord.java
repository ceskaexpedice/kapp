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
@JacksonXmlRootElement(localName = "heslo")
public class MarcRecord  {

    @JacksonXmlProperty(localName = "leader")
    @Field
    public String leader;
    
    @JacksonXmlProperty(localName = "controlfield")
    public List<String> controlfield  = new ArrayList<String>();
    
    @JacksonXmlProperty(localName = "datafield")
    public List<String> datafield  = new ArrayList<String>();

}
