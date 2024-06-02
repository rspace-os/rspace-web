package com.researchspace.archive;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class KeyValue {
  @XmlAnyElement String oldLink;
  @XmlAnyElement String newLink;

  public KeyValue() {}

  public KeyValue(String key, String value) {
    this.oldLink = key;
    this.newLink = value;
  }

  public String getOldLink() {
    return oldLink;
  }

  public String getNewLink() {
    return newLink;
  }

  public void setOldLink(String key) {
    this.oldLink = key;
  }

  public void setNewLink(String value) {
    this.newLink = value;
  }
}
