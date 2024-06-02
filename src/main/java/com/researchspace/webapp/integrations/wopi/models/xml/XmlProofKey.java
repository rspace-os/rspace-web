package com.researchspace.webapp.integrations.wopi.models.xml;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;
import lombok.Getter;

/** POJO for reading the Office Online proof key from the discovery XML */
@XmlType
@Getter
public class XmlProofKey {

  @XmlAttribute(name = "oldvalue")
  private String oldValue;

  @XmlAttribute(name = "oldmodulus")
  private String oldModulus;

  @XmlAttribute(name = "oldexponent")
  private String oldExponent;

  @XmlAttribute private String value;

  @XmlAttribute private String modulus;

  @XmlAttribute private String exponent;
}
