package com.researchspace.service;

/** Specify the desired behaviour if the file to be saved already exists at the given path */
public enum FileDuplicateStrategy {

  /** Do not save the file at all; the original file remains */
  ERROR,
  /** Replace current file with new file */
  REPLACE,
  /** Modify the path so that the file is saved as a new file */
  AS_NEW
}
