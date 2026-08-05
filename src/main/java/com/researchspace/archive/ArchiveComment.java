package com.researchspace.archive;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlType;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@XmlType
@XmlAccessorType(XmlAccessType.FIELD)
@Getter
@Setter
@ToString
public class ArchiveComment {

  private String author;

  private String lastUpdater;

  private Date createDate, updateDate;

  @XmlElementWrapper
  @XmlElement(name = "commentItem")
  private List<ArchiveCommentItem> items;

  public Date getCreateDate() {
    return createDate == null ? null : new Date(createDate.getTime());
  }

  public Date getUpdateDate() {
    return updateDate == null ? null : new Date(updateDate.getTime());
  }
}
