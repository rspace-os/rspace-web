package com.researchspace.api.v1.model;

/**
 * Simplified version of RecordType for exposure in API, using the better-named 'DOCUMENT' rather
 * than 'NORMAL' to indicate a record is a Basic or Structured Document
 */
public enum ApiRecordType {
  FOLDER,
  NOTEBOOK,
  DOCUMENT,
  MEDIA
}
