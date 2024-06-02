/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.axiope.search.SearchConstants;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** SearchTerms */
@Data
@EqualsAndHashCode
@NoArgsConstructor
public class ApiSearchTerm {

  private static final String LOCAL_DATE_REGEXP = "^\\d{4}\\-\\d{2}\\-\\d{2}$";

  @JsonProperty("query")
  private String query = null;

  @JsonProperty("queryType")
  private QueryTypeEnum queryType = null;

  public enum QueryTypeEnum {
    @JsonProperty(SearchConstants.ALL_SEARCH_OPTION)
    GLOBAL(SearchConstants.ALL_SEARCH_OPTION),

    @JsonProperty(SearchConstants.FULL_TEXT_SEARCH_OPTION)
    FULLTEXT(SearchConstants.FULL_TEXT_SEARCH_OPTION),

    @JsonProperty(SearchConstants.TAG_SEARCH_OPTION)
    TAG(SearchConstants.TAG_SEARCH_OPTION),

    @JsonProperty(SearchConstants.NAME_SEARCH_OPTION)
    NAME(SearchConstants.NAME_SEARCH_OPTION),

    @JsonProperty(SearchConstants.CREATION_DATE_SEARCH_OPTION)
    CREATED(SearchConstants.CREATION_DATE_SEARCH_OPTION),

    @JsonProperty(SearchConstants.MODIFICATION_DATE_SEARCH_OPTION)
    LASTMODIFIED(SearchConstants.MODIFICATION_DATE_SEARCH_OPTION),

    @JsonProperty(SearchConstants.FORM_SEARCH_OPTION)
    FORM(SearchConstants.FORM_SEARCH_OPTION),

    @JsonProperty(SearchConstants.ATTACHMENT_SEARCH_OPTION)
    ATTACHMENT(SearchConstants.ATTACHMENT_SEARCH_OPTION),

    @JsonProperty(SearchConstants.OWNER_SEARCH_OPTION)
    OWNER(SearchConstants.OWNER_SEARCH_OPTION),

    @JsonProperty(SearchConstants.RECORDS_SEARCH_OPTION)
    RECORDS(SearchConstants.RECORDS_SEARCH_OPTION);

    private String value;

    QueryTypeEnum(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }

  public ApiSearchTerm(String query, QueryTypeEnum queryType) {
    this.query = query;
    this.queryType = queryType;
  }

  private boolean isDateTerm() {
    return QueryTypeEnum.LASTMODIFIED.equals(getQueryType())
        || QueryTypeEnum.CREATED.equals(getQueryType());
  }

  /** Handles date queries using local dates, e.g. 2014-02-02, converting to UTC start of day */
  public void updateTermToISO8601IfDateTerm() {
    if (isDateTerm()) {
      String[] dateTermStrings = query.split(";");
      if (dateTermStrings[0].matches(LOCAL_DATE_REGEXP)) {
        dateTermStrings[0] =
            LocalDate.parse(dateTermStrings[0]).atStartOfDay().atOffset(ZoneOffset.UTC).toString();
      }
      if (dateTermStrings.length == 2 && dateTermStrings[1].matches(LOCAL_DATE_REGEXP)) {
        dateTermStrings[1] =
            LocalDate.parse(dateTermStrings[1])
                .atTime(23, 59, 59)
                .atOffset(ZoneOffset.UTC)
                .toString();
      }
      setQuery(
          dateTermStrings[0] + ";" + ((dateTermStrings.length == 2) ? dateTermStrings[1] : ""));
    }
  }
}
