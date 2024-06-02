package com.researchspace.archive;

import com.researchspace.core.util.version.SemanticVersion;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;

/** Model object to represent the structure of a parsed archive */
public class ArchiveModel implements IArchiveModel {
  @Override
  public String toString() {
    return "ArchiveModel [formScheme="
        + formScheme
        + ", xmlScheme="
        + xmlScheme
        + ", linkResolver="
        + linkResolver
        + ", manifestFile="
        + manifestFile
        + "]";
  }

  // holds content of archives, including revisions
  private List<ArchivalDocumentParserRef> allVersions = new ArrayList<>();
  private List<ArchivalDocumentParserRef> currentVersions = new ArrayList<>();
  private List<ArchivalGalleryMetaDataParserRef> mediaDocs = new ArrayList<>();
  private ImmutableExportRecordList exportList;

  private File formScheme;

  private File xmlScheme;
  private File linkResolver;
  private File manifestFile;
  private File folderTree;
  private File userInfo;

  private ArchiveManifest manifest;

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.model.archive.IArchiveModel#getFormScheme()
   */
  @Override
  public File getFormScheme() {
    return formScheme;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.model.archive.IArchiveModel#getXmlSchema()
   */
  @Override
  public File getXmlSchema() {
    return xmlScheme;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.model.archive.IArchiveModel#getLinkResolver()
   */
  @Override
  public File getLinkResolver() {
    return linkResolver;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.model.archive.IArchiveModel#getManifestFile()
   */
  @Override
  public File getManifestFile() {
    return manifestFile;
  }

  public void setFormSchema(File formScheme) {
    this.formScheme = formScheme;
  }

  public void setXmlSchema(File xmlScheme) {
    this.xmlScheme = xmlScheme;
  }

  public void setLinkResolver(File linkResolver) {
    this.linkResolver = linkResolver;
  }

  public void setManifestFile(File manifest) {
    this.manifestFile = manifest;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.model.archive.IArchiveModel#getAllVersions()
   */
  @Override
  public List<ArchivalDocumentParserRef> getAllVersions() {
    return allVersions;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.model.archive.IArchiveModel#getCurrentVersions()
   */
  @Override
  public List<ArchivalDocumentParserRef> getCurrentVersions() {
    return currentVersions;
  }

  public boolean addToAll(ArchivalDocumentParserRef ref) {
    return allVersions.add(ref);
  }

  public boolean addToCurrentRevisions(ArchivalDocumentParserRef archivalParserRef) {
    return currentVersions.add(archivalParserRef);
  }

  public boolean addMediaDoc(ArchivalGalleryMetaDataParserRef archivalParserRef) {
    return mediaDocs.add(archivalParserRef);
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.model.archive.IArchiveModel#getTotalRecordCount()
   */
  @Override
  public int getTotalRecordCount() {
    return allVersions.size();
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.model.archive.IArchiveModel#getCurrentRecordCount()
   */
  @Override
  public int getCurrentRecordCount() {
    return currentVersions.size();
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.model.archive.IArchiveModel#getManifest()
   */
  @Override
  public ArchiveManifest getManifest() throws IOException {
    if (manifest == null) {
      manifest = parseManifestFile();
    }
    return manifest;
  }

  // key = up to 1st colon, value is all after.
  public static final Pattern MANIFEST_PATTERN = Pattern.compile("^([^:]+):(.+)$");

  private ArchiveManifest parseManifestFile() throws IOException {
    if (manifestFile == null) {
      throw new IllegalStateException("Cannot parse manifest file as it is null");
    }
    List<String> lines = FileUtils.readLines(manifestFile);
    ArchiveManifest manif = new ArchiveManifest();
    for (String line : lines) {
      Matcher m = MANIFEST_PATTERN.matcher(line);
      if (m.matches()) {
        manif.addItem(m.group(1).trim(), m.group(2).trim());
      }
    }
    return manif;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * com.axiope.model.archive.IArchiveModel#findCurrentDocArchiveByName(java.
   * lang.String)
   */
  @Override
  public List<ArchivalDocumentParserRef> findCurrentDocArchiveByName(String srchTerm) {
    List<ArchivalDocumentParserRef> docs = new ArrayList<>();
    for (ArchivalDocumentParserRef toSearch : currentVersions) {
      if (toSearch.getArchivalDocument().getName().equals(srchTerm)) {
        docs.add(toSearch);
      }
    }
    return docs;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.model.archive.IArchiveModel#getMediaDocs()
   */
  @Override
  public List<ArchivalGalleryMetaDataParserRef> getMediaDocs() {
    return mediaDocs;
  }

  /**
   * Sets the File containgin the folder tree mappings
   *
   * @param folderTree
   */
  public void setFolderTree(File folderTree) {
    this.folderTree = folderTree;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.model.archive.IArchiveModel#getFolderTree()
   */
  @Override
  public File getFolderTree() {
    return folderTree;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.model.archive.IArchiveModel#getFolderTreeList()
   */
  @Override
  public ImmutableExportRecordList getFolderTreeList() {
    return exportList;
  }

  public void setFolderTreeList(ImmutableExportRecordList rl) {
    this.exportList = rl;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.model.archive.IArchiveModel#getCurrentDocCount()
   */
  @Override
  public int getCurrentDocCount() {
    return currentVersions.size();
  }

  public void setUserInfo(File userInfo) {
    this.userInfo = userInfo;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.model.archive.IArchiveModel#getUserInfo()
   */
  @Override
  public File getUserInfo() {
    return userInfo;
  }

  @Override
  public SemanticVersion getSourceRSpaceVersion() {
    try {
      return getManifest().getRSpaceAppVersion();
    } catch (IOException ie) {
      return SemanticVersion.UNKNOWN_VERSION;
    }
  }
}
