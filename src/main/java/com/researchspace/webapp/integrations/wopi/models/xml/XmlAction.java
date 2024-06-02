package com.researchspace.webapp.integrations.wopi.models.xml;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;
import lombok.Getter;

/** POJO for reading an action supported by an app for Office Online in the discovery XML */
@XmlType
@Getter
public class XmlAction {

  @XmlAttribute private String name;

  @XmlAttribute(name = "ext")
  private String fileExtension;

  /** URL to call office online in order to execute this action */
  @XmlAttribute(name = "urlsrc")
  private String urlSource;

  @XmlAttribute(name = "default")
  private boolean isAppDefault;

  @XmlAttribute private String requires;

  @XmlAttribute(name = "targetext")
  private String targetExtension;
}
