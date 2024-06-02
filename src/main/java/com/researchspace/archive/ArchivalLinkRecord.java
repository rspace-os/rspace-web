package com.researchspace.archive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tracks linked documents */
public class ArchivalLinkRecord {

  // map of export file to new doc id
  Map<String, String> nameIdMap;

  Map<Long, Long> oldDocIdToNewDocId = new HashMap<>();

  List<String> sourceFieldIds;

  public ArchivalLinkRecord() {
    nameIdMap = new HashMap<String, String>();
    sourceFieldIds = new ArrayList<String>();
  }

  /**
   * Adds an export filename key -to - new document ID value
   *
   * @param key
   * @param val
   */
  public void addMap(String key, String val) {
    nameIdMap.put(key, val);
  }

  public String getNameId(String name) {
    return nameIdMap.get(name);
  }

  public void addSourceFieldId(String fld_id) {
    sourceFieldIds.add(fld_id);
  }

  public void addOldIdToNewIdMapping(Long oldDocId, Long newDocId) {
    oldDocIdToNewDocId.put(oldDocId, newDocId);
  }

  /**
   * Gets unmodifiable map of old to new Ids
   *
   * @return
   */
  public Map<Long, Long> getOldDocIdToNewDocId() {
    return Collections.unmodifiableMap(oldDocIdToNewDocId);
  }

  /**
   * Gets map of export file name to doc Id
   *
   * @return
   */
  public Map<String, String> getNameIdMap() {
    return nameIdMap;
  }

  public List<String> getSourceFieldIds() {
    return sourceFieldIds;
  }
}
