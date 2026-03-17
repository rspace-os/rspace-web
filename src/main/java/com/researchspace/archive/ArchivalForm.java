package com.researchspace.archive;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** RSForm in archival structure, field reflection, and XML annotation */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "form")
@Getter
@Setter
public class ArchivalForm {
  @XmlAttribute(name = "id", required = true)
  long formId;

  @XmlElement String code;

  @XmlElement String type;

  @XmlElement String name;

  @XmlElement long createDate;

  @XmlElement long modificationDate;

  @XmlElement String publishingState;

  @XmlElement String formVersion;

  @XmlElement String stableId;

  /**
   * The version of the XML schema for this object. This is to avoid needing to change the namespace
   * when schema versions change.
   *
   * @return
   */
  @XmlElement(required = true)
  private Integer schemaVersion = 1;

  public Integer getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(Integer schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  @XmlElementWrapper(name = "listFormFields")
  @XmlElement(name = "fieldForm")
  private List<ArchivalFieldForm> fieldFormList;

  public ArchivalForm() {
    fieldFormList = new ArrayList<>();
    code = "parentCode_form";
  }
}
