package com.researchspace.archive;

/** The source of the archive - a Group, User or Selection */
public enum ExportScope {
  /** A selection of individual records or folders is to be exported, from a single user's work. */
  SELECTION,

  /** A user's data is to be exported. This includes all records and Gallery contents */
  USER,

  /** A group's data is to be exported - each user's Gallery items and records. */
  GROUP;
}
