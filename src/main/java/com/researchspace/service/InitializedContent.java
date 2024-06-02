package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import lombok.Value;

@Value
public class InitializedContent {

  private Folder userRoot;
  private User user;
  private UserFolderSetup folder;
}
