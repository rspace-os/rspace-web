package com.researchspace.webapp.integrations.wopi.models.xml;

import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import lombok.Getter;

/** POJO For reading information about a supported Office Online app from the discovery XML */
@XmlType(name = "app")
@Getter
public class XmlApp {

  @XmlAttribute private String name;

  @XmlAttribute private String favIconUrl;

  @XmlAttribute private String bootstrapperUrl;

  @XmlAttribute private String applicationBaseUrl;

  @XmlAttribute private String staticResourceOrigin;

  @XmlAttribute private Boolean checkLicense;

  @XmlElement(name = "action")
  private List<XmlAction> actions;

  public List<XmlAction> getActions() {
    if (actions == null) return Collections.emptyList();
    else return actions;
  }
}
