package com.researchspace.api.v1.model;

import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.record.DOCUMENT_CATEGORIES;
import com.researchspace.model.record.Folder;
import java.io.Serializable;
import java.util.Random;

public class EcatAudioFileStub extends EcatMediaFile implements Serializable {

  private static final long serialVersionUID = 1L;
  private Folder stubParentFolder;

  public EcatAudioFileStub(Long id, String filename) {
    setId(id);
    setName(filename);
    setFileName(filename);
    Folder parent = new Folder();
    parent.setId(new Random().nextLong());
    stubParentFolder = parent;
  }

  @Override
  public String getRecordInfoType() {
    return DOCUMENT_CATEGORIES.ECATAUDIO;
  }

  @Override
  public Folder getParent() {
    return stubParentFolder;
  }
}
