package com.researchspace.model.core;

import org.apache.commons.lang3.ArrayUtils;

/** Identifier used to classify the different records in the system. */
public enum RecordType {
  /** A standard record created by a user */
  NORMAL,

  /** A root record (Root folder) */
  ROOT,

  /** An example record to illustrate a form */
  NORMAL_EXAMPLE,

  /** A media file */
  MEDIA_FILE,

  /** A regular folder */
  FOLDER,

  /** Specific type for lab notebook folder */
  NOTEBOOK,

  /** Root folder of the media gallery */
  ROOT_MEDIA,

  /** Designates the root of a group's shared folder - collaboration group or labGroup. */
  SHARED_GROUP_FOLDER_ROOT,

  /**
   * The root of a folder tree shared between 2 individuals. Will usually have a name like
   * 'user1-user2'
   */
  INDIVIDUAL_SHARED_FOLDER_ROOT,

  /** The top-level shared folder */
  SHARED_FOLDER,

  /** Specify the template */
  TEMPLATE,

  SNIPPET,
  /** A folder or file created by the system that not not be manipulable by the end-user. */
  SYSTEM,

  /** API Inbox folder */
  API_INBOX,

  /** Imports folder */
  IMPORTS,

  /** - provisional - for openLab exploration */
  DECORATED;

  public String getString() {
    return this.name();
  }

  /**
   * Gets the GlobaId prefix for a given record type:
   *
   * @param recordType a colon separated string of {@link RecordType} names, or a single {@link
   *     RecordType} name
   * @return
   * @throws Exception if recordType cannot be parsed into 1 or more {@link RecordType}.
   */
  public static GlobalIdPrefix getGlobalIdFromType(String recordType) {

    String[] types = recordType.split(":");
    for (String type : types) {
      RecordType.valueOf(type);
    }
    if (ArrayUtils.contains(types, RecordType.SNIPPET.name())) {
      return GlobalIdPrefix.ST;
    } else if (ArrayUtils.contains(types, RecordType.NOTEBOOK.name())) {
      return GlobalIdPrefix.NB;
    } else if (ArrayUtils.contains(types, RecordType.MEDIA_FILE.name())) {
      return GlobalIdPrefix.GL;
    } else if (ArrayUtils.contains(types, RecordType.NORMAL.name())
        || (ArrayUtils.contains(types, RecordType.TEMPLATE.name())
            && !ArrayUtils.contains(types, RecordType.FOLDER.name()))) {
      /* rather FIXME complex if condition to ensure templates folder doesn't return SD (see RSPAC-1690) */
      return GlobalIdPrefix.SD;
    } else {
      /* FIXME we should rather check for RecordType.FOLDER earlier in the ifs,
       * and for default case throw some unknown type exception */
      // default is a folder
      return GlobalIdPrefix.FL;
    }
  }

  public static boolean isNotebookOrFolder(String recordType) {
    return recordType.contains(FOLDER.toString()) || recordType.contains(NOTEBOOK.toString());
  }

  public static boolean isFolder(String recordType) {
    return isNotebookOrFolder(recordType);
  }

  public static boolean isNotebook(String recordType) {
    return recordType.contains(NOTEBOOK.toString());
  }

  public static boolean isMediaFile(String recordType) {
    return recordType.contains(MEDIA_FILE.toString());
  }

  /**
   * Boolean test as to whether the type is a Snippet
   *
   * @param recordType
   * @return
   */
  public static boolean isSnippet(String recordType) {
    return recordType.contains(SNIPPET.toString());
  }

  private static final String TEMPLATE_TYPE = "TEMPLATE";

  public static boolean isDocumentOrTemplate(String recordType) {
    return recordType.contains(TEMPLATE_TYPE) || recordType.contains(NORMAL.name());
  }

  public static boolean isRootMedia(String recordType) {
    return recordType.contains(ROOT_MEDIA.name());
  }
}
