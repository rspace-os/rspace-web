package com.researchspace.export.pdf;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommentAppendix {
  private String commentName;
  // comments
  private List<String> itemNames;
  private List<String> itemDates;
  private List<String> itemContents;

  public CommentAppendix() {
    itemNames = new ArrayList<String>();
    itemDates = new ArrayList<String>();
    itemContents = new ArrayList<String>();
    commentName = "Comment";
  }

  // single eleemnt
  public String getItemName(int idx) {
    return itemNames.get(idx);
  }

  public String getItemDate(int idx) {
    return itemDates.get(idx);
  }

  public String getItemContent(int idx) {
    return itemContents.get(idx);
  }

  public void setItemName(String lst) {
    itemNames.add(lst);
  }

  public void setItemDate(String lst) {
    itemDates.add(lst);
  }

  public void setItemContent(String lst) {
    itemContents.add(lst);
  }

  public int getSize() {
    return itemNames.size();
  }

  public void add(String username, String date, String content) {
    itemNames.add(username + " on ");
    itemDates.add(date + ":  ");
    itemContents.add(content);
  }
}
