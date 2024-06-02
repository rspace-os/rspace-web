/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.api.v1.controller.ApiPaginationCriteria;
import com.researchspace.api.v1.controller.ApiSearchConfig;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.User;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

/** Common fields for paginated API search results. */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class ApiPaginatedResultList<T extends LinkableApiObject>
    extends LinkableApiObject {

  @JsonProperty("totalHits")
  private Long totalHits = null;

  @JsonProperty("pageNumber")
  private Integer pageNumber = null;

  public void addNavigationLinks(
      UriComponentsBuilder apiBaseURI, ApiPaginationCriteria pgCrit, ApiSearchConfig srchConfig) {

    int totalhits = getTotalHits().intValue();
    int currPage = getPageNumber();
    int pageSize = pgCrit.getPageSize();
    // create prev link if possible:
    if (currPage > 0) {
      ApiPaginationCriteria previous = pgCrit.previousPage();
      addLink(buildNavigationLinks(apiBaseURI, previous, srchConfig), ApiLinkItem.PREV_REL);
      // add firstLink if 'prev' is not first page
      if (previous.getPageNumber() > 0) {
        ApiPaginationCriteria first = pgCrit.firstPage();
        addLink(buildNavigationLinks(apiBaseURI, first, srchConfig), ApiLinkItem.FIRST_REL);
      }
    }
    // create next link if there are more results.
    if (totalhits > (currPage + 1) * pageSize) {
      ApiPaginationCriteria previous = pgCrit.nextPage();
      addLink(buildNavigationLinks(apiBaseURI, previous, srchConfig), ApiLinkItem.NEXT_REL);
      int lastPageIndex = nextIsNotLast(totalhits, currPage, pageSize);
      if (lastPageIndex != -1) {
        ApiPaginationCriteria last = pgCrit.lastPage(lastPageIndex);
        addLink(buildNavigationLinks(apiBaseURI, last, srchConfig), ApiLinkItem.LAST_REL);
      }
    }
    addSelfLink(buildNavigationLinks(apiBaseURI, pgCrit, srchConfig));
  }

  private int nextIsNotLast(int totalhits, int currPage, int pageSize) {
    int currPage1Based = currPage + 1;
    int nextPage1Based = currPage1Based + 1;
    if (totalhits > nextPage1Based * pageSize) {
      return getLastPageIndex(totalhits, pageSize);
    } else {
      return -1;
    }
  }

  private int getLastPageIndex(int totalhits, int pageSize) {
    int fullPages = totalhits / pageSize;
    int lastPageElems = totalhits % pageSize;
    return fullPages + (lastPageElems > 0 ? 1 : 0) - 1;
  }

  protected abstract String getSearchEndpoint();

  protected String buildNavigationLinks(
      UriComponentsBuilder apiBaseURI, ApiPaginationCriteria pgCrit, ApiSearchConfig srchConfig) {
    return apiBaseURI
        .cloneBuilder()
        .path(getSearchEndpoint())
        .queryParams(pgCrit.toMap())
        .queryParams(
            srchConfig != null ? srchConfig.toMap() : new LinkedMultiValueMap<String, String>())
        .build()
        .encode()
        .toUriString();
  }

  /**
   * Generic setter for result objects
   *
   * @param items
   */
  public abstract void setItems(List<T> items);

  public <R> ApiPaginatedResultList<T> build(
      ApiPaginationCriteria pgCrit,
      ApiSearchConfig srchConfig,
      User user,
      ISearchResults<R> internalSearchResults,
      Function<R, T> internalModelToApiModel,
      Consumer<T> linkBuilder,
      UriComponentsBuilder uriBuilder) {
    List<T> apiItemList = new ArrayList<>();
    if (internalSearchResults != null) {
      this.setTotalHits(internalSearchResults.getTotalHits());
      this.setPageNumber(internalSearchResults.getPageNumber());
      for (R internalSrchResult : internalSearchResults.getResults()) {
        T apiInfo = internalModelToApiModel.apply(internalSrchResult);
        linkBuilder.accept(apiInfo);
        apiItemList.add(apiInfo);
      }
    }
    this.setItems(apiItemList);
    this.addNavigationLinks(uriBuilder, pgCrit, srchConfig);
    return this;
  }
}
