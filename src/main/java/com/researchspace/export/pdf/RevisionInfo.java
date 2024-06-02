package com.researchspace.export.pdf;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Revision information. */
@Getter
@Setter
public class RevisionInfo {
  private String appendixName;
  private List<String> versions;
  private List<String> dates;
  private List<String> names;
  private List<String> modifyTypes;
  private boolean empty;

  public RevisionInfo() {
    versions = new ArrayList<String>();
    dates = new ArrayList<String>();
    names = new ArrayList<String>();
    modifyTypes = new ArrayList<String>();
    appendixName = "Provenance Information";
    empty = true;
  }

  public String getVersion(int idx) {
    return versions.get(idx);
  }

  public String getDate(int idx) {
    return dates.get(idx);
  }

  public String getName(int idx) {
    return names.get(idx);
  }

  public String getModifyType(int idx) {
    return modifyTypes.get(idx);
  }

  public void setVersion(String lst) {
    versions.add(lst);
  }

  public void setDate(String lst) {
    dates.add(lst);
  }

  public void setName(String lst) {
    names.add(lst);
  }

  public void setModifyType(String lst) {
    modifyTypes.add(lst);
  }

  public int getSize() {
    return versions.size();
  }

  public void add(String docVersion, String dateStr, String username, String modificationType) {
    versions.add(docVersion);
    dates.add(dateStr);
    names.add(username);
    modifyTypes.add(modificationType);
  }
}
