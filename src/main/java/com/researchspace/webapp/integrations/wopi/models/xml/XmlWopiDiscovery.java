package com.researchspace.webapp.integrations.wopi.models.xml;

import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Getter;

/** POJO for reading the root of the Office Online discovery XML */
@XmlRootElement(name = "wopi-discovery")
@Getter
public class XmlWopiDiscovery {

  @XmlElementWrapper(name = "net-zone")
  @XmlElement(name = "app")
  private List<XmlApp> apps;

  public List<XmlApp> getApps() {
    if (apps == null) return Collections.emptyList();
    else return apps;
  }

  @XmlElement(name = "proof-key")
  private XmlProofKey proofKey;
}
