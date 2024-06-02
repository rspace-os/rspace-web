package com.researchspace.netfiles;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystemInfo;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.Data;

@Data
public class NfsExportPlan implements Serializable {

  private static final long serialVersionUID = -185110466997087462L;

  private String planId;
  private List<NfsFileSystemWithLinks> foundFileSystems = new ArrayList<>();

  // maps with filesystem id + "_" + filepath as key
  private Map<String, NfsElement> foundNfsLinks = new TreeMap<>();
  private transient Map<String, NfsResourceDetails> checkedNfsLinks = new TreeMap<>();
  private Map<String, String> checkedNfsLinkMessages = new HashMap<>();

  private int maxArchiveSizeMBProp; // the hard limit for archive size
  private int currentlyAllowedArchiveSizeMB; // export fitting this size will be probably allowed

  @JsonIgnore private Map<Long, NfsFileStore> foundFileStoresByIdMap = new HashMap<>();

  public void addFoundFileSystem(NfsFileSystemInfo nfsFileSystem) {
    NfsFileSystemWithLinks fsWithLinks = new NfsFileSystemWithLinks(nfsFileSystem);
    if (!foundFileSystems.contains(fsWithLinks)) {
      foundFileSystems.add(fsWithLinks);
    }
  }

  public void addFoundNfsLink(Long fileSystemId, String absolutePath, NfsElement nfsLink) {
    String foundNfsFileKey = fileSystemId + "_" + absolutePath;
    if (!foundNfsLinks.containsKey(foundNfsFileKey)) {
      foundNfsLinks.put(foundNfsFileKey, nfsLink);
      getFileSystemWithLinksById(fileSystemId).getFoundNfsLinks().add(nfsLink);
    }
  }

  public void addCheckedNfsLink(
      Long fileSystemId, String absolutePath, NfsResourceDetails nfsResource) {
    String checkedNfsFileKey = fileSystemId + "_" + absolutePath;
    if (!checkedNfsLinks.containsKey(checkedNfsFileKey)) {
      checkedNfsLinks.put(checkedNfsFileKey, nfsResource);
      getFileSystemWithLinksById(fileSystemId).getCheckedNfsLinks().add(nfsResource);
    }
  }

  public void addCheckedNfsLinkMsg(Long fileSystemId, String absolutePath, String msg) {
    String checkedNfsLinkMsgKey = fileSystemId + "_" + absolutePath;
    if (!checkedNfsLinkMessages.containsKey(checkedNfsLinkMsgKey)) {
      checkedNfsLinkMessages.put(checkedNfsLinkMsgKey, msg);
      getFileSystemWithLinksById(fileSystemId).getCheckedNfsLinkMessages().put(absolutePath, msg);
    }
  }

  public boolean isFileAlreadyChecked(Long fileSystemId, String absolutePath) {
    return checkedNfsLinks.containsKey(fileSystemId + "_" + absolutePath);
  }

  public void clearCheckedNfsLinks() {
    getCheckedNfsLinks().clear();
    getCheckedNfsLinkMessages().clear();

    for (NfsFileSystemWithLinks fs : getFoundFileSystems()) {
      fs.getCheckedNfsLinks().clear();
      fs.getCheckedNfsLinkMessages().clear();
    }
  }

  public void addFoundFileStore(NfsFileStore nfsFileStore) {
    Long fileStoreId = nfsFileStore.getId();
    if (!foundFileStoresByIdMap.containsKey(fileStoreId)) {
      foundFileStoresByIdMap.put(fileStoreId, nfsFileStore);
    }
  }

  public long countFileSystemsRequiringLogin() {
    return foundFileSystems.stream().filter(fs -> fs.getLoggedAs() == null).count();
  }

  /*
   * ===================
   *   helper methods
   * ===================
   */
  private NfsFileSystemWithLinks getFileSystemWithLinksById(Long fileSystemId) {
    return foundFileSystems.stream()
        .filter(fs -> fs.getId().equals(fileSystemId))
        .findAny()
        .orElse(null);
  }
}
