package com.researchspace.webapp.controller;

import static com.researchspace.service.impl.DocumentTagManagerImpl.RSPACTAGS_FORSL__;

import com.axiope.search.SearchConstants;
import com.axiope.search.SearchManager;
import com.axiope.search.SearchQueryParseException;
import com.axiope.search.WorkspaceSearchInputValidator;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.field.ChoiceField;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.TextField;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.JournalEntry;
import com.researchspace.service.RecordSigningManager;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.session.UserSessionTracker;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * The following controller feeds information to the jquery journal plugin. It was used for browsing
 * workspace documents in the past, but now it's just for notebook view.
 */
@Controller
@RequestMapping({"/journal*", "/public/publicView/journal*"})
public class JournalController extends BaseController {

  private static final int NUM_HISTORY_ITEMS = 7;
  private static final String HISTORY_SESSION_KEY = "EcatJournalControllerHistory";
  private static final int THIS_DOC = 0;
  private static final int NEXT_DOC = 1;

  private SearchManager searchManager;
  private @Autowired SystemPropertyPermissionManager sysPropPermissionsMgr;

  @Value("${server.urls.prefix}")
  private String baseURL;

  @Autowired
  public void setSearchManager(SearchManager searchManager) {
    this.searchManager = searchManager;
  }

  private RecordSigningManager signingManager;

  @Autowired
  public void setSigningManager(RecordSigningManager signingManager) {
    this.signingManager = signingManager;
  }

  /** Load notebook entry by record id (and notebook id) */
  @GetMapping("/ajax/retrieveEntryById/{notebookId}/{recordId}")
  @ResponseBody
  public JournalEntry retrieveEntry(
      @PathVariable("notebookId") Long notebookId,
      @PathVariable("recordId") Long recordId,
      Principal principal) {

    User user = userManager.getUserByUsername(principal.getName());
    return retrieveEntryByRecordIdOrPosition(notebookId, recordId, null, null, user);
  }

  /** Load notebook entry after opening the notebook or using previuos/next entry arrows */
  @GetMapping("/ajax/retrieveEntry/{notebookId}/{position}/{positionmodifier}")
  @ResponseBody
  public JournalEntry retrieveEntry(
      @PathVariable("notebookId") Long notebookId,
      @PathVariable("position") Integer position,
      @PathVariable("positionmodifier")
          Integer positionModifier, // used when clicking on arrow tags
      Principal principal) {

    User user = userManager.getUserByUsername(principal.getName());
    return retrieveEntryByRecordIdOrPosition(notebookId, null, position, positionModifier, user);
  }

  private JournalEntry retrieveEntryByRecordIdOrPosition(
      Long notebookId, Long recordId, Integer position, Integer positionModifier, User user) {

    // this only retrieves structured documents
    List<Long> allRecordIds = recordManager.getDescendantRecordIdsExcludeFolders(notebookId);
    List<Record> readableRecords = recordManager.getLoadableNotebookEntries(user, notebookId);

    // this is returned if user is unauthorised but not sure why?
    // mk: this is used for new notebook too
    if (readableRecords == null || readableRecords.size() == 0) {
      return new JournalEntry("EMPTY", "");
    }

    // this is the index of requested position, or null if retrieving by id
    Integer requestedEntryPosition = null;
    if (position != null && positionModifier != null) {
      requestedEntryPosition = position + positionModifier;
    }

    // this is the counter of readable documents that were processed
    int readableRecordPosition = 0;

    // need to iterate from the beginning, to skip entries that user can't access
    for (int allRecordPosition = 0; allRecordPosition < allRecordIds.size(); allRecordPosition++) {
      // iterate over unreadable records
      if (!ObjectUtils.equals(
          allRecordIds.get(allRecordPosition),
          readableRecords.get(readableRecordPosition).getId())) {
        continue;
      }

      Record currentRecord = readableRecords.get(readableRecordPosition);
      boolean recordFound =
          requestedEntryPosition != null && requestedEntryPosition.equals(readableRecordPosition)
              || recordId != null && recordId.equals(currentRecord.getId());
      if (recordFound) {
        return retrieveJournalEntry(currentRecord, user, allRecordPosition);
      }

      readableRecordPosition++;
    }

    // entry not found
    JournalEntry noResultEntry = new JournalEntry("NO_RESULT", "");
    if (positionModifier == THIS_DOC || positionModifier == NEXT_DOC) {
      noResultEntry.setPosition(readableRecords.size());
    } else {
      noResultEntry.setPosition(-1);
    }
    return noResultEntry;
  }

  /* Creates a journal entry based on the type of record */
  private JournalEntry retrieveJournalEntry(BaseRecord currentRecord, User user, Integer position) {

    StructuredDocument doc =
        recordManager.getRecordWithFields(currentRecord.getId(), user).asStrucDoc();
    JournalEntry journalEntry = new JournalEntry(doc, prepareStructuredDocumentContent(doc));
    journalEntry.setBaseURL(baseURL);
    journalEntry.setPosition(position);
    if (doc.getDocTag() != null) {
      journalEntry.setTags(doc.getDocTag().replaceAll(RSPACTAGS_FORSL__, "/"));
    }
    if (doc.getTagMetaData() != null) {
      journalEntry.setTagMetaData(doc.getTagMetaData().replaceAll(RSPACTAGS_FORSL__, "/"));
    }
    UserSessionTracker activeUsers = getCurrentActiveUsers();
    journalEntry.setEditStatus(
        recordManager.requestRecordView(currentRecord.getId(), user, activeUsers));
    signingManager.updateSigningAttributes(journalEntry, doc.getId(), user);

    boolean canShare =
        user.equals(doc.getOwner()) && permissionUtils.isPermitted(doc, PermissionType.SHARE, user);
    journalEntry.setCanShare(canShare);

    boolean canSign = doc.isAllFieldsValid() && !doc.isSigned();
    journalEntry.setCanSign(canSign);

    return journalEntry;
  }

  // rspac-359 LoM support
  private static final String LIST_OF_MATERIALS_DIV =
      "<div class='invMaterialsListing' data-field-id='%d'></div>";

  private static final String EXTERNAL_WORKFLOWS_DIV =
      "<div class='galaxy-textfield' data-field-id='%d''></div>";

  /* Creates html string containing all the named fields and contents. Escapes content of non-text fields. */
  protected String prepareStructuredDocumentContent(StructuredDocument doc) {
    StringBuffer buffer = new StringBuffer();
    List<Field> allFields = doc.getFields();
    Boolean inventoryEnabled = null;
    Boolean galaxyEnabled = null;
    for (Field field : allFields) {
      if (!doc.isBasicDocument()) {
        buffer.append("<h2 class='formTitles'>" + field.getName() + "</h2>");
      }
      if (field instanceof TextField) {
        // just calculate this once if needed if there are multiple text fields
        if (inventoryEnabled == null) {
          inventoryEnabled =
              sysPropPermissionsMgr.isPropertyAllowed((User) null, "inventory.available");
        }
        if (inventoryEnabled) {
          buffer.append(String.format(LIST_OF_MATERIALS_DIV, field.getId()));
        }
        if (galaxyEnabled == null) {
          galaxyEnabled = sysPropPermissionsMgr.isPropertyAllowed((User) null, "galaxy.available");
        }
        if (galaxyEnabled) {
          buffer.append(String.format(EXTERNAL_WORKFLOWS_DIV, field.getId()));
        }
      }
      Field latestField = field;
      if (field.getTempField() != null) {
        // RSTEST-284: show autosaved content, if present
        latestField = field.getTempField();
      }

      String fieldContent;
      if (latestField instanceof ChoiceField) {
        fieldContent = ((ChoiceField) latestField).getChoiceOptionSelectedAsString();
      } else {
        fieldContent = latestField.getFieldData();
      }
      if (!(latestField instanceof TextField)) {
        fieldContent = StringEscapeUtils.escapeHtml(fieldContent);
      }

      buffer.append(fieldContent);
      buffer.append("<br/><br/>");
    }
    return buffer.toString();
  }

  @SuppressWarnings("unchecked")
  @GetMapping("/ajax/retrieveHistory/{recordid}/{position}/{refresh}")
  @ResponseBody
  public ResponseEntity<List<JournalEntry>> retrieveHistory(
      @PathVariable("recordid") Long notebookId,
      @PathVariable("position") Integer position,
      @PathVariable("refresh") Boolean refresh,
      HttpSession session,
      Principal principal) {

    User u = userManager.getUserByUsername(principal.getName());

    List<Record> readableRecords = null;
    if (refresh) {
      readableRecords = recordManager.getLoadableNotebookEntries(u, notebookId);
      session.setAttribute(HISTORY_SESSION_KEY, readableRecords);
    } else {
      readableRecords = (List<Record>) session.getAttribute(HISTORY_SESSION_KEY);
    }

    // position is 'page' of history, 1-based. e.g., 15 entries; page is 1,2,or 3
    int historyEndPosition = (position * NUM_HISTORY_ITEMS);
    int historyStartPosition = historyEndPosition - NUM_HISTORY_ITEMS;

    boolean moreItemsAvailable = true;
    if (readableRecords != null && historyEndPosition >= readableRecords.size()) {
      historyEndPosition = readableRecords.size();
      moreItemsAvailable = false;
    }

    List<JournalEntry> results = new ArrayList<JournalEntry>();
    if (readableRecords != null
        && readableRecords.size() > 0
        && position <= readableRecords.size()
        && position >= 0) {
      for (int i = historyStartPosition; i < historyEndPosition; i++) {
        BaseRecord record = readableRecords.get(i);
        log.debug("name: " + record.getName());
        results.add(new JournalEntry(record, ""));
      }
    }

    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.set("MoreItemsAvailable", Boolean.toString(moreItemsAvailable));
    return new ResponseEntity<List<JournalEntry>>(results, responseHeaders, HttpStatus.OK);
  }

  // search checks permissions
  @GetMapping("/ajax/quicksearch/{recordid}/{pagenum}/{term}")
  @ResponseBody
  public ResponseEntity<List<JournalEntry>> searchText(
      @PathVariable String term,
      @PathVariable("recordid") Long recordId,
      @PathVariable("pagenum") Integer pageNum,
      Principal subject) {

    String options[] = {SearchConstants.ALL_SEARCH_OPTION};
    String[] terms = {term};
    PaginationCriteria<BaseRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(BaseRecord.class);
    pgCrit.setPageNumber((long) pageNum);
    pgCrit.setResultsPerPage(NUM_HISTORY_ITEMS);
    WorkspaceListingConfig config =
        new WorkspaceListingConfig(pgCrit, options, terms, recordId, false);

    // Set notebookFilter = true to filter the results by notebook in
    // FullTextSearcher.java, by default is false.
    config.setNotebookFilter(true);

    BindingResult br = new BeanPropertyBindingResult(config, "config");
    User user = userManager.getAuthenticatedUserInSession();
    new WorkspaceSearchInputValidator(user).validate(config, br);
    if (br.hasErrors()) {
      return null;
    }

    List<JournalEntry> results = new ArrayList<JournalEntry>();
    boolean hasMoreResults = false;

    try {
      // The method "searchManager.searchRecords()" filters the results by
      // permission, so we retrieve only records which
      // the user can see and they are included in the recordId (Notebook)
      // in this case.
      if (user.isAnonymousGuestAccount()) {
        Notebook nb = folderManager.getNotebook(recordId);
        if (!nb.isPublished()) {
          throw new AuthorizationException(
              "Anonymous user searching unpublished records - likely a hacking attempt");
        }
        user = nb.getOwner();
      }
      ISearchResults<BaseRecord> recordsx = searchManager.searchWorkspaceRecords(config, user);
      if (recordsx.getResults().isEmpty()) {
        return null;
      }

      // Create a series of journal entries and find and set correct
      // position from lineage list
      List<Long> currentRecords = recordManager.getDescendantRecordIdsExcludeFolders(recordId);

      for (BaseRecord record : recordsx.getResults()) {
        JournalEntry entry = new JournalEntry(record, "");
        entry.setPosition(currentRecords.indexOf(record.getId()));
        results.add(entry);
      }

      // If we are not at the end of the results, inform the caller via a header
      hasMoreResults = ((pageNum + 1) * NUM_HISTORY_ITEMS) < recordsx.getTotalHits();

    } catch (IOException | SearchQueryParseException e) {
      // user can freely swipe in the journal which cause the above
      // search manager logic to through exceptions
      // to prevent this spamming the log we catch the exception and don't
      // log it. Instead just a warning
      log.warn("No results were found, search manager threw an exception: " + e.getMessage());
      return null;
    }

    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.set("MoreItemsAvailable", Boolean.toString(hasMoreResults));
    return new ResponseEntity<List<JournalEntry>>(results, responseHeaders, HttpStatus.OK);
  }

  void setSysPropPermissionsMgr(SystemPropertyPermissionManager sysPropPermissionsMgr) {
    this.sysPropPermissionsMgr = sysPropPermissionsMgr;
  }
}
