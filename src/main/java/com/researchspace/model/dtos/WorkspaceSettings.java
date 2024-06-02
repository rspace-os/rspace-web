package com.researchspace.model.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.record.BaseRecord;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * This represents the workspace state on the client side, including:
 *
 * <ul>
 *   <li>Pagination settings (sort order, orderby and records per page)
 *   <li>Search settings
 *   <li>Workspace filters (favourites etc)
 * </ul>
 *
 * It's a convenience class that will populate itself from any request params with the same name as
 * field names in this class, in order to reduce the number of method parameters in the Workspace
 * controller methods. For ease of conversion from request parameters, it's a flat structure, and
 * therefore lacks the
 *
 * <p>This class is very similar to {@link WorkspaceListingConfig} and may be combined in future. At
 * present this purely a POJO to encapuslate the many incoming request parameters defining the
 * workspace state.
 *
 * <h3>Maintenance</h3>
 *
 * New properties in Workspace settings should be added here and in the Javascript object in
 * workspace.js
 *
 * @see WorkspaceSettingsUML
 */
@Data
@NoArgsConstructor
@Component
@Scope("request")
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkspaceSettings implements Serializable {

  public enum WorkspaceViewMode {
    LIST_VIEW,
    TREE_VIEW
  }

  private static final long serialVersionUID = 1L;

  // filters
  private boolean sharedFilter;
  private boolean favoritesFilter;
  private boolean viewableItemsFilter;
  private boolean templatesFilter;
  private boolean ontologiesFilter;

  // search config
  private Long parentFolderId;

  /**
   * Grandparent folder – used for determining if entry deletion from shared notebooks should be an
   * actual deletion, or an unshare. Pretty backwards way of solving that problem, would be better
   * if delete button always deleted, and maybe we added a "unshare" button to crudops menu.
   */
  private Long grandparentFolderId;

  private boolean advancedSearch;
  private SearchOperator operator = SearchOperator.AND;
  private boolean notebookFilter;

  // pagination criteria
  private String orderBy;
  private SortOrder sortOrder = SortOrder.DESC;
  private Long pageNumber = 0L;
  private Integer resultsPerPage;

  // Other settings
  private WorkspaceViewMode currentViewMode;

  /**
   * To convert a {@link WorkspaceListingConfig} back to a {@link WorkspaceSettings} for
   * serialisation back to UI
   *
   * @param workspaceListingConfig
   */
  public WorkspaceSettings(WorkspaceListingConfig workspaceListingConfig) {
    // filters
    sharedFilter = workspaceListingConfig.getFilters().isSharedFilter();
    favoritesFilter = workspaceListingConfig.getFilters().isFavoritesFilter();
    viewableItemsFilter = workspaceListingConfig.getFilters().isViewableItemsFilter();
    templatesFilter = workspaceListingConfig.getFilters().isTemplatesFilter();
    ontologiesFilter = workspaceListingConfig.getFilters().isOntologiesFilter();
    // search
    parentFolderId = workspaceListingConfig.getParentFolderId();
    grandparentFolderId = workspaceListingConfig.getParentFolderId();
    advancedSearch = workspaceListingConfig.isAdvancedSearch();
    operator = workspaceListingConfig.getOperator();
    notebookFilter = workspaceListingConfig.isNotebookFilter();
    // pagination
    orderBy = workspaceListingConfig.getPgCrit().getOrderBy();
    sortOrder = workspaceListingConfig.getPgCrit().getSortOrder();
    pageNumber = workspaceListingConfig.getPgCrit().getPageNumber();
    resultsPerPage = workspaceListingConfig.getPgCrit().getResultsPerPage();
    // other
    currentViewMode = workspaceListingConfig.getCurrentViewMode();
  }

  /** Creates a {@link WorkspaceFilters} object from the relevant properties in this class */
  public WorkspaceFilters createWorkspaceFilter() {
    WorkspaceFilters filters = new WorkspaceFilters();
    filters.setSharedFilter(sharedFilter);
    filters.setFavoritesFilter(favoritesFilter);
    filters.setViewableItemsFilter(viewableItemsFilter);
    filters.setTemplatesFilter(templatesFilter);
    filters.setOntologiesFilter(ontologiesFilter);
    return filters;
  }

  /** Creates a {@link PaginationCriteria} object from the relevant properties in this class */
  public PaginationCriteria<BaseRecord> createPaginationCriteria() {
    PaginationCriteria<BaseRecord> pg = PaginationCriteria.createDefaultForClass(BaseRecord.class);
    pg.setOrderBy(orderBy);
    pg.setPageNumber(pageNumber);
    pg.setSortOrder(sortOrder);
    pg.setResultsPerPage(resultsPerPage);
    return pg;
  }

  /**
   * Boolean test for whether search term arrays have any contents
   *
   * @param options
   * @param terms
   * @return
   */
  public static boolean isSearchTermsSet(String[] options, String[] terms) {
    return options != null && terms != null && options.length > 0 && terms.length > 0;
  }

  /**
   * Converts a {@link WorkspaceSettings} to JSON, in a form capable of being parsed in to a
   * WorkspaceSettings object in Javascript, adding in search options and terms.
   */
  public String toJson(String[] options, String[] terms) {
    ObjectMapper om = new ObjectMapper();
    ObjectNode node = om.valueToTree(this);
    ArrayNode opt = om.valueToTree(options);
    ArrayNode term = om.valueToTree(terms);

    node.set("options", opt);
    node.set("terms", term);
    return JacksonUtil.toJson(node);
  }

  /**
   * Converts a {@link WorkspaceSettings} to JSON, in a form capable of being parsed in to a
   * WorkspaceSettings object in Javascript, adding in an empty array for 'options' and 'terms'
   */
  public String toJson() {
    ObjectMapper om = new ObjectMapper();
    ObjectNode node = om.valueToTree(this);

    node.set("options", om.createArrayNode());
    node.set("terms", om.createArrayNode());
    return JacksonUtil.toJson(node);
  }
}
