package com.researchspace.archive;

import com.researchspace.model.core.Person;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = {"uniqueName"})
public class ArchiveUser implements Person {

  private String email, fullName, uniqueName, id;
}
