package com.researchspace.webapp.controller;

import java.util.HashMap;
import java.util.Map;

/** Transfers permissions to control UI display/available actions. */
public class ActionPermissionsDTO {
  public ActionPermissionsDTO() {}

  private boolean createFolder = true;
  private boolean createRecord = true;
  private boolean deleteRecord = true;

  private Map<Long, Map<String, Boolean>> instancePermissions =
      new HashMap<Long, Map<String, Boolean>>();

  public Map<Long, Map<String, Boolean>> getInstancePermissions() {
    return instancePermissions;
  }

  private boolean rename = true;

  public void setPermissionForRecord(Long id, String string, Boolean enabled) {
    if (instancePermissions.get(id) == null) {
      Map<String, Boolean> permMap = new HashMap<String, Boolean>();
      instancePermissions.put(id, permMap);
    }
    instancePermissions.get(id).put(string, enabled);
  }

  public boolean isDeleteRecord() {
    return deleteRecord;
  }

  public void setDeleteRecord(boolean delete) {
    this.deleteRecord = delete;
  }

  public boolean isMove() {
    return move;
  }

  public void setMove(boolean move) {
    this.move = move;
  }

  public boolean isCopy() {
    return copy;
  }

  public void setCopy(boolean copy) {
    this.copy = copy;
  }

  private boolean createNotebook = true;
  private boolean move = false;
  private boolean copy = false;

  public boolean isCreateFolder() {
    return createFolder;
  }

  public void setCreateFolder(boolean createFolder) {
    this.createFolder = createFolder;
  }

  public boolean isCreateRecord() {
    return createRecord;
  }

  public void setCreateRecord(boolean createRecord) {
    this.createRecord = createRecord;
  }

  public boolean isCreateNotebook() {
    return createNotebook;
  }

  public void setCreateNotebook(boolean createNotebook) {
    this.createNotebook = createNotebook;
  }

  public void setRename(boolean rename) {
    this.rename = rename;
  }

  public boolean isRename() {
    return rename;
  }
}
