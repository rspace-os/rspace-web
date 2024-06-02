package com.researchspace.service;

import com.researchspace.model.record.Folder;

public interface UserFolderSetup {

  Folder getUserRoot();

  Folder getTemplateFolder();

  Folder getMediaRoot();

  Folder getExamples();

  Folder getMediaImgExamples();

  Folder getShared();

  Folder getSnippet();
}
