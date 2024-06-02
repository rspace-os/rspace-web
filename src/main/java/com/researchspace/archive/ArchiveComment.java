package com.researchspace.archive;

import java.util.Date;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;
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
