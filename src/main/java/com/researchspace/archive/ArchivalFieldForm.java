package com.researchspace.archive;

import java.util.Date;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@XmlAccessorType(XmlAccessType.FIELD)
public class ArchivalFieldForm {
  @XmlAttribute(name = "id", required = true)
  private Long formFieldId;

  @XmlElement String code;

  @XmlElement private String name;

  @XmlElement private int columnIndex;

  @XmlElement private Long modificationDate;

  @XmlElement String type;

  @XmlElement private String helpText;

  @XmlElement private String summary;

  @XmlElement private String min;

  @XmlElement private String max;

  @XmlElement private String defaultValue;

  @XmlElement private String options;

  @XmlElement private String selection;

  @XmlElement boolean isPassword;

  @XmlElement private String decimalPlace;

  @XmlElement private String multipleChoice;

  @XmlElement private boolean displayAsPickList;

  @XmlElement private boolean sortAlphabetic;

  @XmlElement(name = "dateFormat")
  private String dateFormat;

  public ArchivalFieldForm() {
    code = "parentCode_formField";
    modificationDate = new Date().getTime();
  }
}
