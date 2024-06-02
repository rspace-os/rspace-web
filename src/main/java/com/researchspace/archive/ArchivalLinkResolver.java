package com.researchspace.archive;

import com.researchspace.core.util.XMLReadWriteUtils;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Original Link and new Link Resolver. Keeps a mapping between original URLs used as src links and
 * new
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "linkResolverMap")
public class ArchivalLinkResolver {
  @XmlElement(required = true)
  private Integer schemaVersion = 1;

  /**
   * The version of the XML schema for this object. This is to avoid needing to change the namespace
   * when schema versions change.
   *
   * @return
   */
  public Integer getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(Integer schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  @XmlElement(name = "linkMap")
  @XmlJavaTypeAdapter(value = MapAdapter.class)
  Map<String, String> older2New;

  @XmlElement(name = "newToOld")
  @XmlJavaTypeAdapter(value = MapAdapter.class)
  Map<String, String> new2Older;

  public ArchivalLinkResolver() {
    older2New = new HashMap<String, String>();
    new2Older = new HashMap<String, String>();
  }

  public void addLinks(String olderLink, String newLink) {
    older2New.put(olderLink, newLink);
    new2Older.put(newLink, olderLink);
  }

  public void setOlder2New(Map<String, String> older2New) {
    this.older2New = older2New;
  }

  public Map<String, String> getOlder2New() {
    return older2New;
  }

  public String getOlderLink(String newLink) {
    return new2Older.get(newLink);
  }

  public String getNewLink(String olderLink) {
    return older2New.get(olderLink);
  }

  public Iterator<String> getOlderLinks() {
    return new2Older.keySet().iterator();
  }

  public Iterator<String> getNewLinks() {
    return older2New.keySet().iterator();
  }

  public void outputXML(File ouF) throws Exception {
    XMLReadWriteUtils.toXML(ouF, this, ArchivalLinkResolver.class);
  }
}
