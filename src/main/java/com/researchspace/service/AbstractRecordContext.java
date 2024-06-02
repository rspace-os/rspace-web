package com.researchspace.service;

import lombok.Getter;
import lombok.Setter;

abstract class AbstractRecordContext {

  public AbstractRecordContext(boolean recursiveFolderOperation) {
    this.recursiveFolderOperation = recursiveFolderOperation;
  }

  @Getter @Setter private boolean recursiveFolderOperation = false;
}
