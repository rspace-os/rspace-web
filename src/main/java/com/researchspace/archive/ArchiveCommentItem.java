package com.researchspace.archive;

import java.util.Date;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@XmlType
@XmlAccessorType(XmlAccessType.FIELD)
public class ArchiveCommentItem {

  private String lastUpdater;
  private Date createDate;
  private Date updateDate;
  private String itemContent;

  public Date getCreateDate() {
    return createDate == null ? null : new Date(createDate.getTime());
  }

  public Date getUpdateDate() {
    return updateDate == null ? null : new Date(updateDate.getTime());
  }
}
