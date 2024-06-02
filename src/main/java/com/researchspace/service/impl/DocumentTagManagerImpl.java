package com.researchspace.service.impl;

import static com.researchspace.service.impl.OntologyDocManager.RSPACE_EXTONTOLOGY_NAME_DELIM;
import static com.researchspace.service.impl.OntologyDocManager.RSPACE_EXTONTOLOGY_NAME_ONTOLOGYFILE;
import static com.researchspace.service.impl.OntologyDocManager.RSPACE_EXTONTOLOGY_TAG_DELIMITER;
import static com.researchspace.service.impl.OntologyDocManager.RSPACE_EXTONTOLOGY_URL_DELIMITER;
import static com.researchspace.service.impl.OntologyDocManager.RSPACE_EXTONTOLOGY_VERSION_DELIM;
import static com.researchspace.service.impl.OntologyDocManager.RSPACE_EXTONTOLOGY_VERSION_ONTOLOGYFILES;
import static org.apache.commons.lang.StringUtils.isBlank;

import com.researchspace.dao.FolderDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.TaggableElnRecord;
import com.researchspace.model.User;
import com.researchspace.model.dtos.RecordTagData;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.DetailedRecordInformation;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.MessagedServiceOperationResult;
import com.researchspace.service.DetailedRecordInformationProvider;
import com.researchspace.service.DocumentTagManager;
import com.researchspace.service.inventory.InventoryTagApiManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DocumentTagManagerImpl implements DocumentTagManager {
  private int minUpdateIntervalMillis = 1000;
  public static final String RSPACTAGS_FORSL__ = "__rspactags_forsl__";
  public static final String RSPACTAGS_COMMA__ = "__rspactags_comma__";
  private @Autowired RecordDao recordDao;
  private @Autowired FolderDao folderDao;
  private @Autowired RecordGroupSharingDao sharingDao;
  private @Autowired IPermissionUtils permissnUtils;
  @Autowired private InventoryTagApiManager inventoryTagApiManager;
  @Autowired private OntologyDocManager ontologyDocManager;
  @Autowired private DetailedRecordInformationProvider detailedRecordInformationProvider;

  @Autowired private BioPortalOntologiesService bioPortalOntologiesService;

  /**
   * Store timestamps of ontology doc updates by user - used so that batch edits in Iventory dont
   * update the ontology doc for every record in the batch.
   */
  private Map<User, Long> userAndLastTimeOntologyDocUpdated = new HashMap<>();

  public static final String getTagOntologyUriFromMeta(String tagAndMeta) {
    if (tagAndMeta.indexOf(RSPACE_EXTONTOLOGY_URL_DELIMITER) == -1) {
      return "";
    }
    String value =
        tagAndMeta
            .split(RSPACE_EXTONTOLOGY_URL_DELIMITER)[1]
            .split(RSPACE_EXTONTOLOGY_NAME_DELIM)[0];
    return value;
  }

  public static final String getTagOntologyNameFromMeta(String tagAndMeta) {
    if (tagAndMeta.indexOf(RSPACE_EXTONTOLOGY_URL_DELIMITER) == -1) {
      return "";
    }
    String value =
        tagAndMeta
            .split(RSPACE_EXTONTOLOGY_NAME_DELIM)[1]
            .split(RSPACE_EXTONTOLOGY_VERSION_DELIM)[0];
    return value;
  }

  public static final String getTagOntologyVersionFromMeta(String tagAndMeta) {
    if (tagAndMeta.indexOf(RSPACE_EXTONTOLOGY_URL_DELIMITER) == -1) {
      return "";
    }
    String value = tagAndMeta.split(RSPACE_EXTONTOLOGY_VERSION_DELIM)[1];
    return value;
  }

  public static final String getTagValueFromMeta(String tagAndMeta) {
    if (tagAndMeta.indexOf(RSPACE_EXTONTOLOGY_URL_DELIMITER) == -1) {
      return tagAndMeta;
    }
    String tagValue = tagAndMeta.split(RSPACE_EXTONTOLOGY_URL_DELIMITER)[0];
    return tagValue;
  }

  public static final List<String> getAllTagValuesFromAllTagsPlusMeta(String tagsAndMeta) {
    String[] tagValuesAndMeta = tagsAndMeta.split(",");
    // tag values are not unique globally - but they are unique for a given document.
    return Arrays.stream(tagValuesAndMeta)
        .map(val -> getTagValueFromMeta(val))
        .collect(Collectors.toList());
  }

  @Override
  public int getMinUpdateIntervalMillis() {
    return minUpdateIntervalMillis;
  }

  @Override
  public void setMinUpdateIntervalMillis(int minUpdateIntervalMillis) {
    this.minUpdateIntervalMillis = minUpdateIntervalMillis;
  }

  @Override
  public Set<String> getTagMetaDataForRecordIds(List<Long> ids, User user) {
    return getRecordTagsForRecordIds(ids, user).stream()
        .filter(rtDTO -> StringUtils.isNotEmpty(rtDTO.getTagMetaData()))
        .map(rtDTO -> rtDTO.getTagMetaData())
        .collect(Collectors.toSet());
  }

  @Override
  public List<RecordTagData> getRecordTagsForRecordIds(List<Long> ids, User user) {
    List<RecordTagData> result = new ArrayList<>();
    for (Long id : ids) {
      if (recordDao.isRecord(id)) {
        StructuredDocument sd = recordDao.get(id).asStrucDoc();
        result.add(new RecordTagData(sd.getId(), sd.getTagMetaData()));
      } else {
        Folder fd = folderDao.get(id);
        result.add(new RecordTagData(fd.getId(), fd.getTagMetaData()));
      }
    }
    return result;
  }

  @Override
  public MessagedServiceOperationResult<BaseRecord> saveTag(
      Long recordId, String tagtext, User user) {
    return saveTagForRecordOrFolder(recordId, tagtext, user);
  }

  @Override
  public boolean saveTagsForRecords(List<RecordTagData> recordTagsList, User user) {
    for (RecordTagData recordTag : recordTagsList) {
      if (recordTag.getTagMetaData() != null) {
        saveTagForRecordOrFolder(recordTag.getRecordId(), recordTag.getTagMetaData().trim(), user);
      }
    }
    return true;
  }

  /**
   * NEW tag values saved through the API will be 'local ontology' - THEY CAN NOT HAVE TAG METADATA.
   * Existing tag values will keep their metaData.
   *
   * @param tagtext - tag values that will be saved into the 'tags' and 'tagMetaData' field of a
   *     document, while preserving existing tagMetaData. This is to allow API users to save only
   *     tag values without being aware of the existence of tagMetaData.
   *     <p>Will save 'tagtext' to tags. However, if the values in tagText match an existing
   *     tag/tagMetaData value then no change is saved to the DB (so tagMetaData is preserved). Any
   *     values which dont match existing tag values are saved to tags and tagMetaData. Any existing
   *     values in the DB with no match in tagtext are deleted from the DB tags and tagMetaData
   *     field.
   *     <p>The exception is if 'enforceOntologies' is true. If so, no new tags are saved and only
   *     deletion of existing tags is allowed. Attempts to save new tags are rejected.
   */
  @Override
  public MessagedServiceOperationResult<BaseRecord> apiSaveTagForDocument(
      Long sdocId, String tagtext, User user) {
    if (tagtext == null) {
      return null;
    }
    StructuredDocument sd = (StructuredDocument) recordDao.get(sdocId);
    String[] existingTagText =
        sd.getTagMetaData() != null ? sd.getTagMetaData().split(",") : new String[0];
    Map<String, String> tagValuesToTagPlusMeta =
        Arrays.stream(existingTagText)
            .map(
                tagPlusMeta ->
                    tagPlusMeta.contains(RSPACE_EXTONTOLOGY_URL_DELIMITER)
                        ? new String[] {
                          tagPlusMeta.split(RSPACE_EXTONTOLOGY_URL_DELIMITER)[0], tagPlusMeta
                        }
                        : new String[] {tagPlusMeta, tagPlusMeta})
            .collect(Collectors.toMap(e -> e[0], e -> e[1]));
    String[] newTagValues = tagtext.split(",");
    List<String> valuesToSaveToDB = new ArrayList<>(newTagValues.length);
    boolean ontologiesEnforced = enforceOntologiesIfAnyGroupEnforces(user);
    String[] forbiddenValues = new String[] {"<", ">", "\\", "/"};
    for (String newTagValue : newTagValues) {
      for (String forbidden : forbiddenValues) {
        if (newTagValue.contains(forbidden)) {
          throw new IllegalArgumentException(
              "The value: " + forbidden + " may not be used in tags");
        }
      }
      // existing values saved using existing tagMetaData
      if (tagValuesToTagPlusMeta.containsKey(newTagValue)) {
        valuesToSaveToDB.add(tagValuesToTagPlusMeta.get(newTagValue));
      } else {
        if (ontologiesEnforced && newTagValue.length() > 0) {
          throw new IllegalArgumentException(
              "New tags cannot be saved through the API when ontologies are enforced");
        }
        // new values - will be saved with the given value as tagMetaData (local ontology)
        valuesToSaveToDB.add(newTagValue);
      }
    }
    String dataStringToSaveToDB =
        String.join(",", valuesToSaveToDB.toArray(new String[valuesToSaveToDB.size()]));

    return saveTagForRecordOrFolder(sdocId, dataStringToSaveToDB, user);
  }

  private MessagedServiceOperationResult<BaseRecord> saveTagForRecordOrFolder(
      Long recordId, String tagMetaData, User user) {
    BaseRecord record;
    boolean isRecord = recordDao.isRecord(recordId);
    if (isRecord) {
      record = recordDao.get(recordId);
    } else {
      record = folderDao.get(recordId);
    }
    if (!record.isTaggable()) {
      throw new IllegalArgumentException(
          "Only StructuredDocuments, Folders and Notebooks can be tagged");
    }
    permissnUtils.assertIsPermitted(record, PermissionType.WRITE, user, "save tag");

    String docTag = ((TaggableElnRecord) record).getDocTag();
    if (isBlank(tagMetaData) && isBlank(docTag)) {
      // return success if tags are empty (failure reserved for real failures)
      return new MessagedServiceOperationResult<>(record, true, "");
    }

    String joinedTagValues =
        String.join(",", DocumentTagManagerImpl.getAllTagValuesFromAllTagsPlusMeta(tagMetaData));
    ((TaggableElnRecord) record).setDocTag(joinedTagValues);
    ((TaggableElnRecord) record).setTagMetaData(tagMetaData);
    record.setModificationDate(new Date());
    if (isRecord) {
      recordDao.save((StructuredDocument) record);
    } else {
      folderDao.save((Folder) record);
    }
    ontologyDocManager.writeTagsToUsersOntologyTagDoc(
        user, getTagsPlusMetaForViewableDocuments(user, ""));
    return new MessagedServiceOperationResult<>(record, true, "");
  }

  /**
   * Only updates once per second so that batch Inventory Tag edits *should* only update for the 1st
   * record in the batch
   *
   * @param user
   */
  @Override
  public void updateUserOntologyDocument(User user) {
    Long now = Long.valueOf(System.currentTimeMillis());
    Long lastUpdate = userAndLastTimeOntologyDocUpdated.get(user);
    boolean lastUpdateMoreThanMinInterval = true;
    if (lastUpdate != null) {
      if (now - lastUpdate < getMinUpdateIntervalMillis()) {
        lastUpdateMoreThanMinInterval = false;
      }
    }
    userAndLastTimeOntologyDocUpdated.put(user, now);
    if (lastUpdateMoreThanMinInterval) {
      ontologyDocManager.writeTagsToUsersOntologyTagDoc(
          user, getTagsPlusMetaForViewableDocuments(user, ""));
    }
  }

  @Override
  public TreeSet<String> getTagsForViewableDocuments(User subject, String tagFilter) {
    TreeSet<String> rc = new TreeSet<>();
    populateTagsViewableByUserFromElnAndInventory(subject, tagFilter, rc);
    return rc;
  }

  @Override
  public TreeSet<String> getTagsPlusMetaForViewableDocuments(User subject, String tagFilter) {
    TreeSet<String> rc = new TreeSet<>();
    populateTagsViewableByUserFromElnAndInventory(subject, tagFilter, rc);
    return rc;
  }

  @Override
  public TreeSet<String> getTagsPlusMetaForViewableELNDocuments(User subject, String tagFilter) {
    TreeSet<String> rc = new TreeSet<>();
    populateTagsViewableByUserFromEln(subject, tagFilter, rc);
    return rc;
  }

  @Override
  public TreeSet<String> getTagsPlusMetaForViewableInventoryDocuments(
      User subject, String tagFilter) {
    TreeSet<String> rc = new TreeSet<>();
    populateTagsViewableByUserFromInventory(subject, tagFilter, rc);
    return rc;
  }

  @Override
  public TreeSet<String> getTagsPlusOntologiesForViewableDocuments(
      User subject, String tagFilter, int pos) {
    Set<String> unsorted = getTagsPlusOntologiesViewableByUser(subject, tagFilter);
    TreeSet<String> sorted = new TreeSet<>();
    if (unsorted.size() > MAX_ONTOLOGY_RESULTS_SIZE) {
      sorted.add(TOO_MANY_ONTOLOGY_RESULTS);
      return sorted;
    } else {
      sorted = new TreeSet<>(unsorted);
    }
    if (sorted.size() < ONTOLOGY_RESULTS_PAGE_SIZE) {
      sorted.add(SMALL_DATASET_IN_SINGLE_BLOCK);
      return sorted;
    }
    List<String> chosenData = new ArrayList<>(sorted);
    chosenData.sort(String::compareTo);
    if (ONTOLOGY_RESULTS_PAGE_SIZE * (pos + 1) <= sorted.size()) {
      chosenData =
          chosenData.subList(
              pos * ONTOLOGY_RESULTS_PAGE_SIZE, (pos + 1) * ONTOLOGY_RESULTS_PAGE_SIZE);
    } else {
      chosenData =
          chosenData.subList(
              (sorted.size() / ONTOLOGY_RESULTS_PAGE_SIZE) * ONTOLOGY_RESULTS_PAGE_SIZE,
              sorted.size());
      chosenData.add(FINAL_DATA);
    }
    return new TreeSet(chosenData);
  }

  private Set<String> getTagsPlusOntologiesViewableByUser(User subject, String tagFilter) {
    Set<String> parsedOntologyTerms = new HashSet<>();
    Set<String> tagsPlusOntologyTermsViewableByUserFromStructureDocs = new HashSet<>();
    boolean allOntologiesAndTagsFromDocumentsAreAllowed =
        !enforceOntologiesIfAnyGroupEnforces(subject);
    boolean bioOntologiesAllowed = allGroupsAllowBioOntologies(subject);
    List<String> userPlusSharedOntologiesText = new ArrayList<>();
    List<String> userOntologiesText = null;
    List<String> sharedOntologiesText = null;
    if (allOntologiesAndTagsFromDocumentsAreAllowed) { // all tags in any document/or ontology the
      // user can read are used
      populateTagsViewableByUserFromElnAndInventory(
          subject, tagFilter, tagsPlusOntologyTermsViewableByUserFromStructureDocs);
      userOntologiesText = recordDao.getTextDataFromOntologiesOwnedByUser(subject);
      sharedOntologiesText = sharingDao.getTextDataFromOntologiesSharedWithUser(subject);
    } else { // only tags in Ontology docs shared with a Group are used
      List<BaseRecord> ontologyDocumentsVisibleForUser =
          recordDao.getOntologyFilesOwnedByUser(subject);
      List<Long> ontologyDocumentsBelongingToUserSharedWithAGroup =
          extractIDsOfFilesFilteringForBeingSharedWithAGroup(ontologyDocumentsVisibleForUser);
      userOntologiesText =
          recordDao.getTextDataFromOntologyFilesOwnedByUserIfSharedWithAGroup(
              subject, ontologyDocumentsBelongingToUserSharedWithAGroup.toArray(Long[]::new));
      List<BaseRecord> sharedontologyDocumentsVisibleForUser =
          sharingDao.getOntologiesFilesSharedWithUser(subject);
      List<Long> sharedontologyDocumentsVisibleForUserAlsoSharedWithAGroup =
          extractIDsOfFilesFilteringForBeingSharedWithUsersGroup(
              sharedontologyDocumentsVisibleForUser, subject);
      sharedOntologiesText =
          sharingDao.getTextDataFromOntologiesSharedWithUserIfSharedWithAGroup(
              subject,
              sharedontologyDocumentsVisibleForUserAlsoSharedWithAGroup.toArray(Long[]::new));
    }
    userPlusSharedOntologiesText.addAll(userOntologiesText);
    userPlusSharedOntologiesText.addAll(sharedOntologiesText);
    if (bioOntologiesAllowed) {
      List<String> bioPortalData = bioPortalOntologiesService.getBioOntologyDataForQuery(tagFilter);
      parsedOntologyTerms.addAll(bioPortalData);
    }
    // parsedOntologies contains user ontology text and also text from external ontologies.
    // User ontologies are converted to key=value pairs, external ontologies are not
    List<String> parsedOntologies =
        parseUserOntologiesToKeyValuePairs(userPlusSharedOntologiesText);
    parsedOntologyTerms.addAll(parsedOntologies);
    String filterLc = tagFilter.toLowerCase();
    int count = 0;
    for (String ontologyTerm : parsedOntologyTerms) {
      boolean isFromOntologyFile = false;
      String[] tags = null;
      if (ontologyTerm.contains(
          RSPACE_EXTONTOLOGY_TAG_DELIMITER)) { // tags from external ontology file, imported into
        // RSpace
        isFromOntologyFile = true;
        tags = ontologyTerm.split(RSPACE_EXTONTOLOGY_TAG_DELIMITER);
      } else if (ontologyTerm.contains(RSPACE_EXTONTOLOGY_URL_DELIMITER)) {
        tags = new String[] {ontologyTerm}; // user tags
      } else {
        tags = ontologyTerm.split(StructuredDocument.TAG_DELIMITER);
      }
      String ontologyName = "";
      String ontologyVersion = "";
      if (isFromOntologyFile) {
        // the first two 'tags' are actually the ontology NAME and VERSION
        ontologyName = tags[0].replace(RSPACE_EXTONTOLOGY_NAME_ONTOLOGYFILE, "");
        ontologyVersion = tags[1].replace(RSPACE_EXTONTOLOGY_VERSION_ONTOLOGYFILES, "");
        tags = Arrays.copyOfRange(tags, 2, tags.length); // remove ontology name and version
      }
      for (String tag : tags) {
        String toTest = tag;
        if (toTest.indexOf(RSPACE_EXTONTOLOGY_URL_DELIMITER) != -1) {
          toTest = toTest.split(RSPACE_EXTONTOLOGY_URL_DELIMITER)[0];
        }
        if (toTest.toLowerCase().contains(filterLc)) {
          String term =
              isFromOntologyFile
                  ? tag.trim()
                      + RSPACE_EXTONTOLOGY_NAME_DELIM
                      + ontologyName
                      + RSPACE_EXTONTOLOGY_VERSION_DELIM
                      + ontologyVersion
                  : tag.trim();
          if (tagsPlusOntologyTermsViewableByUserFromStructureDocs.add(term)) {
            count++;
          }
          if (count > MAX_ONTOLOGY_RESULTS_SIZE) {
            return tagsPlusOntologyTermsViewableByUserFromStructureDocs;
          }
        }
      }
    }
    return tagsPlusOntologyTermsViewableByUserFromStructureDocs;
  }

  private boolean enforceOntologiesIfAnyGroupEnforces(User user) {
    for (Group g : user.getGroups()) {
      if (g.isEnforceOntologies()) {
        return true;
      }
    }
    return false;
  }

  public static boolean allGroupsAllowBioOntologies(User user) {
    return (user.getGroups().stream().filter(x -> !x.isProjectGroup()).count() > 0)
        && (user.getGroups().stream()
            .filter(x -> !x.isProjectGroup())
            .allMatch(Group::isAllowBioOntologies));
  }

  private List<Long> extractIDsOfFilesFilteringForBeingSharedWithUsersGroup(
      List<BaseRecord> ontologyDocumentsVisibleForUser, User subject) {
    List<Long> ontologySharedWithUserAndOneOfUsersGroups = new ArrayList<>();
    for (BaseRecord visible : ontologyDocumentsVisibleForUser) {
      DetailedRecordInformation details = new DetailedRecordInformation(visible);
      detailedRecordInformationProvider.addSharingInfo(visible.getId(), visible, details);
      Map<String, String> sharedGroups = details.getSharedGroupsAndAccess();
      if (sharedGroups.size() > 0) {
        for (String gName : sharedGroups.keySet()) {
          for (Group aGroup : subject.getGroups()) {
            if (aGroup.getDisplayName().equals(gName)) {
              ontologySharedWithUserAndOneOfUsersGroups.add(visible.getId());
            }
          }
        }
      }
    }
    return ontologySharedWithUserAndOneOfUsersGroups;
  }

  private List<Long> extractIDsOfFilesFilteringForBeingSharedWithAGroup(
      List<BaseRecord> ontologyDocumentsSharedWithUser) {
    List<Long> ontologyDocumentsSharedWithAGroupVisibleForUser = new ArrayList<>();
    for (BaseRecord visible : ontologyDocumentsSharedWithUser) {
      DetailedRecordInformation details = new DetailedRecordInformation(visible);
      detailedRecordInformationProvider.addSharingInfo(visible.getId(), visible, details);
      Map<String, String> sharedGroups = details.getSharedGroupsAndAccess();
      if (sharedGroups.size() > 0) {
        ontologyDocumentsSharedWithAGroupVisibleForUser.add(visible.getId());
      }
    }
    return ontologyDocumentsSharedWithAGroupVisibleForUser;
  }

  private void populateTagsViewableByUserFromEln(
      User subject, String tagFilter, Set<String> viewAbleTags) {
    populateTagsViewableByUser(subject, tagFilter, viewAbleTags, true, false);
  }

  private void populateTagsViewableByUserFromInventory(
      User subject, String tagFilter, Set<String> viewAbleTags) {
    populateTagsViewableByUser(subject, tagFilter, viewAbleTags, false, true);
  }

  private void populateTagsViewableByUserFromElnAndInventory(
      User subject, String tagFilter, Set<String> viewAbleTags) {
    populateTagsViewableByUser(subject, tagFilter, viewAbleTags, true, true);
  }

  private void populateTagsViewableByUser(
      User subject,
      String tagFilter,
      Set<String> viewAbleTags,
      boolean useELN,
      boolean useInventory) {
    tagFilter = tagFilter.replaceAll("/", RSPACTAGS_FORSL__).replaceAll(",", RSPACTAGS_COMMA__);
    Set<String> tmp = new HashSet<>();
    if (useELN) {
      if (subject.hasRole(Role.SYSTEM_ROLE)) {
        List<String> commTags =
            recordDao.getTagsMetaDataForRecordsVisibleBySystemAdmin(subject, tagFilter);
        tmp.addAll(commTags);
      } else if (subject.hasAdminRole()) {
        List<String> commTags =
            recordDao.getTagsMetaDataForRecordsVisibleByCommunityAdmin(subject, tagFilter);
        tmp.addAll(commTags);
      } else {
        List<String> ownerTags =
            recordDao.getTagsMetaDataForRecordsVisibleByUserOrPi(subject, tagFilter);
        List<String> sharedWithMeTags =
            sharingDao.getTagsMetaDataForRecordsSharedWithUser(subject, tagFilter);
        tmp.addAll(sharedWithMeTags);
        tmp.addAll(ownerTags);
      }
    }
    if (useInventory) {
      tmp.addAll(inventoryTagApiManager.getTagsForUser(subject));
    }
    tmp = tmp.stream().filter(t -> t != null).collect(Collectors.toSet());

    for (String tagSet : tmp) {
      for (String tag : tagSet.split(",")) {
        if (tag.toLowerCase().indexOf(tagFilter.toLowerCase()) != -1) {
          tag = tag.replaceAll(RSPACTAGS_FORSL__, "/");
          viewAbleTags.add(tag.trim());
        }
      }
    }
  }

  /**
   * Each block of text here is the rtfData from a field in an ontology file User ontologies
   * (intended to be human readable, therefore formatted with html) have a different format to
   * external ontologies (not intended to be human readable). This code parses user ontologies and
   * does nothing to external ontologies. User ontologies can specify key value pairs in the format
   * <key>=<val1>,<val2> etc where the values are comma delimited. This code will convert that into
   * multiple tags, each containing the key and '=' plus val1, then val2 etc. <key>=<val1>,<val2>
   * would become two tags, key=val1 and key=val2
   *
   * @param ontologies
   * @return
   */
  @NotNull
  private List<String> parseUserOntologiesToKeyValuePairs(List<String> ontologies) {
    return ontologies.stream()
        .map(text -> parseOntologiesToKeyValuePairs(text))
        .flatMap(Collection::stream)
        .filter(text -> !StringUtils.isEmpty(text))
        .collect(Collectors.toList());
  }

  private List<String> parseOntologiesToKeyValuePairs(String text) {
    // user created ontology files all start with '<p>'
    if (text.length() > 2 && !text.substring(0, 3).equals("<p>")) {
      return List.of(
          text); // assumption - no <p> in an uploaded external ontology file as first char
      // note - if edited an uploaded files does get <p> inserted, hence we have further checks
      // below.
    }
    List<String> parsed = new ArrayList<>();
    String[] allOntologyTermsInFile = text.split("</p>");
    for (String ontologyEntry : allOntologyTermsInFile) {
      ontologyEntry = ontologyEntry.replaceFirst("<p>", "");
      int splitPosOnFirstEquals = ontologyEntry.indexOf("=");
      if (splitPosOnFirstEquals != -1) {
        String key = ontologyEntry.substring(0, splitPosOnFirstEquals);
        String allValueText = ontologyEntry.substring(splitPosOnFirstEquals + 1);
        // if key has commas, assume we are trying to parse an uploaded file by accident
        // that contains a <p> and an '='
        if (key.split(",").length > 1) {
          return List.of(text); // stop parsing
        }
        String[] values = allValueText.split(",");
        for (String val : values) {
          parsed.add(key + "=" + val);
        }
      } else {
        parsed.add(ontologyEntry);
      }
    }
    return parsed;
  }
}
