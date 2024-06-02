package com.researchspace.archive;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public abstract class AbstractArchivalParserRef {

  String name;
  List<File> fileList = new ArrayList<>();
  File path;
  String documentFileName;

  public AbstractArchivalParserRef() {
    fileList = new ArrayList<File>();
  }

  public void addFile(File fx) {
    fileList.add(fx);
  }

  public boolean isMedia() {
    return false;
  }

  public boolean isDocument() {
    return false;
  }

  public long getRevision() {
    return 0;
  }
}
