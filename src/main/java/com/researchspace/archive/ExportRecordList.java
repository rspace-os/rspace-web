package com.researchspace.archive;

import static java.util.stream.Collectors.toCollection;

import com.researchspace.model.core.GlobalIdentifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Represents the XML representation of a Folder tree and of items to export. <br>
 * Stores IDs of items that are permitted to be exported.
 *
 * <p>TODO this class has 2 roles now - to store list of items to export, and also folder tree
 * structure, should be split.
 */
@XmlRootElement
public class ExportRecordList implements ImmutableExportRecordList {

  private Integer schemaVersion = 1;

  /**
   * The version of the XML schema for this object. This is to avoid needing to change the namespace
   * when schema versions change.
   *
   * @return
   */
  @XmlElement(required = true)
  public Integer getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(Integer schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  private List<GlobalIdentifier> recordsToExport = new ArrayList<>();
  private Set<GlobalIdentifier> associatedFieldAttachments = new HashSet<>();

  private List<ArchiveFolder> foldersToExport = new ArrayList<ArchiveFolder>();

  /**
   * Get immutable read-only view of the record Ids to export
   *
   * @return
   */
  @XmlTransient
  public List<GlobalIdentifier> getRecordsToExport() {
    return Collections.unmodifiableList(recordsToExport);
  }

  /**
   * Get immutable read-only view of the FieldAttachments to include in export
   *
   * @return
   */
  @XmlTransient
  public Set<GlobalIdentifier> getAssociatedFieldAttachments() {
    return Collections.unmodifiableSet(associatedFieldAttachments);
  }

  /**
   * number of records in export list
   *
   * @return
   */
  @Override
  @XmlTransient
  public int getRecordsToExportSize() {
    return recordsToExport.size();
  }

  /**
   * Gets 1st record id to export
   *
   * @return
   */
  @Override
  @XmlTransient
  public GlobalIdentifier getFirstRecordToExport() {
    return recordsToExport.get(0);
  }

  @Override
  @XmlElementWrapper(name = "listFolders")
  public List<ArchiveFolder> getFolderTree() {
    return foldersToExport;
  }

  /**
   * Merges <code>toMerge</code> into this list. Merges recordlist, foldersToExport and
   * associaedFieldAttachments.
   *
   * @param toMerge
   */
  public void add(ImmutableExportRecordList toMerge) {
    this.recordsToExport.addAll(toMerge.getRecordsToExport());
    this.foldersToExport.addAll(toMerge.getFolderTree());
    this.associatedFieldAttachments.addAll(toMerge.getAssociatedFieldAttachments());
  }

  /**
   * Analyzes the list of ArchiveFolders to find folders whose parent Id is either null or is not
   * included in this folder list;
   *
   * @return A subset of of <code> getFolderTree()</code>which are top-level folders.
   */
  @Override
  public List<ArchiveFolder> getTopLevelFolders() {
    Set<Long> ids = new HashSet<Long>();
    for (ArchiveFolder af : foldersToExport) {
      ids.add(af.getId());
    }
    List<ArchiveFolder> topLevelFolders = new ArrayList<ArchiveFolder>();
    for (ArchiveFolder af : foldersToExport) {
      // parent id is the folder from which this folder was exported;
      // therefore this folder is a top-level folder
      if (af.getParentId() == null || !ids.contains(af.getParentId())) {
        topLevelFolders.add(af);
      }
    }
    return topLevelFolders;
  }

  /** Gets the Archive folder with the given ID, as an Optional. */
  @Override
  public Optional<ArchiveFolder> getArchiveFolder(Long id) {
    Optional<ArchiveFolder> rc = Optional.empty();
    if (id == null) {
      return rc;
    }
    return foldersToExport.stream().filter(af -> id.equals(af.getId())).findFirst();
  }

  /**
   * Analyzes the list of ArchiveFolders to find folders whose parent Id is the argument Id.
   *
   * @param parentId
   * @return a possibly empty but non-null list.
   */
  @Override
  public List<ArchiveFolder> getChildren(Long parentId) {
    return foldersToExport.stream()
        .filter(af -> parentId.equals(af.getParentId()))
        .collect(toCollection(ArrayList::new));
  }

  public boolean archiveParentFolderMatches(
      Long id, Predicate<ArchiveFolder> parentFolderPredicate) {
    Optional<ArchiveFolder> thisFolderOpt = getArchiveFolder(id);
    return thisFolderOpt.map(af -> parentFolderMatches(af, parentFolderPredicate)).orElse(false);
  }

  boolean parentFolderMatches(
      ArchiveFolder thisFolder, Predicate<ArchiveFolder> parentFolderPredicate) {
    if (!thisFolder.isSystemFolder()) {
      return false;
    }
    if (thisFolder.getParentId() != null) {
      return getArchiveFolder(thisFolder.getParentId()).filter(parentFolderPredicate).isPresent();
    } else {
      return false;
    }
  }

  // delegation methods to hide internal state of recordsToExportList
  public boolean addAll(Collection<GlobalIdentifier> ids) {
    return recordsToExport.addAll(ids);
  }

  /**
   * Add all new GlobalIdentifiers, ignoring duplicates
   *
   * @param ids
   * @return
   */
  public boolean addAllFieldAttachments(Collection<GlobalIdentifier> ids) {
    return associatedFieldAttachments.addAll(ids);
  }

  /**
   * Boolean test as to whether the list of recordsToExport contains the given <code>
   * GlobalIdentifier</code>
   *
   * @param rcdId
   * @return
   */
  @Override
  public boolean containsRecord(GlobalIdentifier rcdId) {
    return recordsToExport.contains(rcdId);
  }

  /**
   * Ignores any version information - permissions do not vary depending on version
   *
   * @param rcdId
   * @return
   */
  @Override
  public boolean containsFieldAttachment(GlobalIdentifier rcdId) {
    return associatedFieldAttachments.stream().anyMatch(oid -> gidEqualsIgnoreVersion(rcdId, oid));
  }

  private boolean gidEqualsIgnoreVersion(GlobalIdentifier rcdId, GlobalIdentifier oid) {
    return oid.getDbId().equals(rcdId.getDbId()) && oid.getPrefix().equals(rcdId.getPrefix());
  }

  @Override
  public boolean containsFolder(Long id) {
    ArchiveFolder fld = new ArchiveFolder();
    fld.setId(id);
    return foldersToExport.contains(fld);
  }

  /**
   * Adds globalId to that of records to export
   *
   * @param id
   * @return
   */
  public boolean add(GlobalIdentifier id) {
    return recordsToExport.add(id);
  }

  /**
   * adds <code>id</code> to the front of records list
   *
   * @param id
   */
  public void prepend(GlobalIdentifier id) {
    recordsToExport.add(0, id);
  }
}
