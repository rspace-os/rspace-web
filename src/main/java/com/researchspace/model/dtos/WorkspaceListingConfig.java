package com.researchspace.model.dtos;

import static com.axiope.search.SearchConstants.*;

import com.axiope.search.SearchConstants;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.record.BaseRecord;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang.Validate;

/** Simple POJO class to hold search input terms for validation */
@NoArgsConstructor
@AuditTrailData(auditDomain = AuditDomain.WORKSPACE)
public class WorkspaceListingConfig implements Serializable {

  private static final long serialVersionUID = 1298041762401038554L;

  public static final List<String> PERMITTED_ORDERBY_FIELDS =
      Arrays.asList(
          "name",
          "modificationDate",
          "creationDate",
          "modificationDateMillis",
          "creationDateMillis");

  @Getter @Setter
  private PaginationCriteria<BaseRecord> pgCrit =
      PaginationCriteria.createDefaultForClass(BaseRecord.class);

  @Getter private String[] srchOptions = new String[] {};

  @Getter private String[] srchTerms = new String[] {};

  @Getter @Setter private Long parentFolderId;

  @Getter @Setter private Long grandparentFolderId;

  @Getter @Setter private boolean advancedSearch = false;

  @Getter private SearchOperator operator = SearchOperator.AND;

  @Getter @Setter private boolean notebookFilter = false;

  @Getter @Setter private WorkspaceFilters filters = new WorkspaceFilters();

  @Getter @Setter
  private WorkspaceSettings.WorkspaceViewMode currentViewMode =
      WorkspaceSettings.WorkspaceViewMode.LIST_VIEW;

  /**
   * @param pgCrit
   * @param options An array of strings of {@link SearchConstants} search options.
   * @param terms An array of strings of search terms, of the same length as options.
   * @param parentFolderId An optional folderId restricting the search to descendants of that folder
   * @param isAdvancedSearch <code>true</code> if is advanced search, false otherwise.
   */
  public WorkspaceListingConfig(
      PaginationCriteria<BaseRecord> pgCrit,
      String[] options,
      String[] terms,
      Long parentFolderId,
      boolean isAdvancedSearch) {
    super();
    this.pgCrit = pgCrit;
    this.srchOptions = Arrays.copyOf(options, options.length);
    this.srchTerms = Arrays.copyOf(terms, terms.length);
    this.parentFolderId = parentFolderId;
    this.advancedSearch = isAdvancedSearch;
    this.filters = new WorkspaceFilters();
  }

  public WorkspaceListingConfig(
      PaginationCriteria<BaseRecord> pgCrit,
      String[] options,
      String[] terms,
      Long parentFolderId,
      boolean isAdvancedSearch,
      String operator,
      WorkspaceFilters filters,
      WorkspaceSettings.WorkspaceViewMode currentViewMode) {
    this(pgCrit, parentFolderId, isAdvancedSearch, operator, filters, currentViewMode);
    this.srchOptions = Arrays.copyOf(options, options.length);
    this.srchTerms = Arrays.copyOf(terms, terms.length);
  }

  /**
   * If some search criteria are set (ie., if is Enabled() == true), this method converts the search
   * criteria to a {@link WorkspaceListingConfig}. Otherwise, returns <code>null</code>.
   *
   * @param pgCrit
   * @param parentId
   */
  public WorkspaceListingConfig(
      PaginationCriteria<BaseRecord> pgCrit, Long parentId, GalleryFilterCriteria galleryFilter) {

    this(
        pgCrit,
        new String[] {SearchConstants.NAME_SEARCH_OPTION},
        new String[] {galleryFilter.getName()},
        parentId,
        false);
  }

  /**
   * Alternative constructor that doesn't take search terms/options, these need to be set separately
   *
   * @param pgCrit
   * @param parentFolderId
   * @param isAdvancedSearch
   * @param operator
   * @param filters
   */
  public WorkspaceListingConfig(
      PaginationCriteria<BaseRecord> pgCrit,
      Long parentFolderId,
      boolean isAdvancedSearch,
      String operator,
      WorkspaceFilters filters,
      WorkspaceSettings.WorkspaceViewMode currentViewMode) {
    super();
    this.pgCrit = pgCrit;
    this.parentFolderId = parentFolderId;
    this.advancedSearch = isAdvancedSearch;
    this.operator = SearchOperator.valueOf(operator);
    this.filters = filters;
    this.currentViewMode = currentViewMode;
  }

  public WorkspaceListingConfig(WorkspaceSettings workspaceSettings) {
    this(
        workspaceSettings.createPaginationCriteria(),
        workspaceSettings.getParentFolderId(),
        workspaceSettings.isAdvancedSearch(),
        workspaceSettings.getOperator().name(),
        workspaceSettings.createWorkspaceFilter(),
        workspaceSettings.getCurrentViewMode());
  }

  /**
   * Combines a {@link WorkspaceSettings} and search terms into a complete description of workspace
   * state.
   *
   * @param workspaceSettings
   * @param options
   * @param terms
   */
  public WorkspaceListingConfig(
      WorkspaceSettings workspaceSettings, String[] options, String[] terms) {
    this(
        workspaceSettings.createPaginationCriteria(),
        options,
        terms,
        workspaceSettings.getParentFolderId(),
        workspaceSettings.isAdvancedSearch(),
        workspaceSettings.getOperator().name(),
        workspaceSettings.createWorkspaceFilter(),
        workspaceSettings.getCurrentViewMode());
  }

  @Override
  public String toString() {
    return "WorkspaceSearchInput [pgCrit="
        + pgCrit.toString()
        + ", options="
        + Arrays.toString(srchOptions)
        + ", terms="
        + Arrays.toString(srchTerms)
        + ", parentFolderId="
        + parentFolderId
        + ", isAdvancedSearch="
        + advancedSearch
        + "]";
  }

  public void setSrchOptions(String[] options) {
    this.srchOptions = Arrays.copyOf(options, options.length);
  }

  public void setSrchTerms(String[] terms) {
    this.srchTerms = Arrays.copyOf(terms, terms.length);
  }

  public void setOperator(SearchOperator operator) {
    Validate.notNull(operator, "operator cannot be null!");
    this.operator = operator;
  }

  public boolean isSimpleNameSearch() {
    return isSimpleSearch(NAME_SEARCH_OPTION, srchOptions);
  }

  public boolean isSimpleModificationDateSearch() {
    return isSimpleSearch(MODIFICATION_DATE_SEARCH_OPTION, srchOptions);
  }

  public boolean isSimpleCreationDateSearch() {
    return isSimpleSearch(CREATION_DATE_SEARCH_OPTION, srchOptions);
  }

  public boolean isSimpleFormSearch() {
    return isSimpleSearch(FORM_SEARCH_OPTION, srchOptions);
  }

  public boolean isAttachmentSearch() {
    return isSimpleSearch(ATTACHMENT_SEARCH_OPTION, srchOptions);
  }

  private boolean isSimpleSearch(String expected, String[] options) {
    return options.length == 1 && expected.equals(srchOptions[0]);
  }

  /**
   * Generates JSON string of this object for return to UI
   *
   * @return
   */
  public String toJson() {
    WorkspaceSettings settings = new WorkspaceSettings(this);
    return settings.toJson(srchOptions, srchTerms);
  }
}
