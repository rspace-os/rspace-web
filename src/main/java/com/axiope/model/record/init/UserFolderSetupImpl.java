package com.axiope.model.record.init;

import com.researchspace.model.record.Folder;
import com.researchspace.service.UserFolderSetup;
import lombok.Data;

@Data
public class UserFolderSetupImpl implements UserFolderSetup {

  private Folder userRoot;
  private Folder shared;
  private Folder examples;
  private Folder mediaRoot;
  private Folder mediaImg;
  private Folder mediaImgExamples;
  private Folder mediaVideo;
  private Folder mediaAudio;
  private Folder mediaChemistry;
  private Folder mediaMiscl;
  private Folder mediaDocs;
  private Folder pdfMedia;
  private Folder templateFolder;
  private Folder snippet;
  private Folder sharedSnippet;
}
