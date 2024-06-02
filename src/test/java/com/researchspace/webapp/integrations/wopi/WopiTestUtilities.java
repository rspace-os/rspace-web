package com.researchspace.webapp.integrations.wopi;

import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.webapp.integrations.wopi.models.xml.XmlWopiDiscovery;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import javax.xml.bind.JAXBException;

public class WopiTestUtilities {

  public static File MSOFFICE_DISCOVERY_XML_FILE =
      RSpaceTestUtils.getResource("officeOnlineDiscovery.xml");

  public static File COLLABORA_DISCOVERY_XML_FILE =
      RSpaceTestUtils.getResource("collaboraOnlineDiscovery.xml");

  public static void setWopiDiscoveryFromExampleFile(
      WopiDiscoveryServiceHandler discoveryServiceHandler,
      WopiDiscoveryProcessor processor,
      File discoveryXmlFile)
      throws FileNotFoundException, JAXBException {
    InputStreamReader reader = new FileReader(discoveryXmlFile);
    XmlWopiDiscovery discovery = processor.parseDiscoveryXml(reader);
    WopiDiscoveryServiceHandler.WopiData data = processor.mapDiscoveryData(discovery);
    // Give the Service the data processed from the example file
    discoveryServiceHandler.setData(data);
  }
}
