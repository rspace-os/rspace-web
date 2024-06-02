package com.axiope.search;

import static com.axiope.search.SearchConstants.ALL_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.CREATION_DATE_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.FULL_TEXT_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.MODIFICATION_DATE_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.RECORDS_SEARCH_OPTION;
import static org.apache.commons.lang.StringUtils.join;

import com.researchspace.model.User;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.validation.Errors;

/**
 * Validator for a {@link WorkspaceListingConfig} configuration object that configures search.<br>
 * A new object should be made for each validation, as this object contains user state.
 */
public class WorkspaceSearchInputValidator extends SearchInputValidator {

  private static final Set<String> SELECTABLE_RECORD_PREFIXES =
      new HashSet<>(Arrays.asList("SD", "NB", "FL"));

  public WorkspaceSearchInputValidator(User subject) {
    super(subject);
  }

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(WorkspaceListingConfig.class);
  }

  /**
   * Validates search terms and other workspace listing settings. Exits as soon as an error is
   * found.
   *
   * @param target WorkspaceListingConfig to validate
   * @param errors object containing errors if any constraint fails
   */
  @Override
  public void validate(Object target, Errors errors) {
    WorkspaceListingConfig input = (WorkspaceListingConfig) target;
    String[] options = input.getSrchOptions();
    String[] terms = input.getSrchTerms();

    validateSearchOptions(options, terms, errors);
    if (errors.hasErrors()) {
      return;
    }

    // eln-specific validation below

    for (int i = 0; i < terms.length; i++) {
      if (options[i].equals(FULL_TEXT_SEARCH_OPTION) || options[i].equals(ALL_SEARCH_OPTION)) {
        if (terms[i].startsWith("*") || terms[i].startsWith("?")) {
          errors.reject("errors.textquerywildcardstartdisallowed", new String[] {terms[i]}, null);
          return;
        }
      }
      if (options[i].equals(RECORDS_SEARCH_OPTION)) {
        if (options.length < 2 || allOptionsAreRecords(options)) {
          errors.reject("errors.recordFilterMustIncludeTerm", null);
          return;
        }
        String[] recordList = terms[i].split("\\s*[,;]\\s*");
        try {
          for (String recordId : recordList) {
            // examples of valid recordIds: FL420, NB1337, SD80085
            if (recordId.length() <= 2) {
              errors.reject("errors.termcannotparse", new String[] {terms[i]}, null);
              return;
            }
            String docType = recordId.substring(0, 2);
            if (!SELECTABLE_RECORD_PREFIXES.contains(docType)) {
              errors.reject("errors.termcannotparse", new String[] {terms[i]}, null);
              return;
            }
            String globalId = recordId.substring(2);
            Long.parseLong(globalId);
          }
        } catch (NumberFormatException e) {
          errors.reject("errors.termcannotparse", new String[] {terms[i]}, null);
          return;
        }
      }
      if (options[i].equals(CREATION_DATE_SEARCH_OPTION)
          || options[i].equals(MODIFICATION_DATE_SEARCH_OPTION)) {
        // validate dates – they should be separated by a semicolon ;
        // and be either empty, or equal "null", or in ISO-8601 format.
        String[] toAndFrom =
            terms[i].split("\\s*[,;]\\s*", -1); // -1 makes sure we also catch empty strings
        if (toAndFrom.length != 2) {
          errors.reject("errors.termcannotparse", new String[] {terms[i]}, null);
          return;
        }
        try {
          if (shouldParseDate(toAndFrom[0])) {
            OffsetDateTime.parse(toAndFrom[0]);
          }
          if (shouldParseDate(toAndFrom[1])) {
            OffsetDateTime.parse(toAndFrom[1]);
          }
        } catch (DateTimeParseException e) {
          errors.reject("errors.termcannotparse", new String[] {terms[i]}, null);
          return;
        }
      }
    }
    String orderBy = input.getPgCrit().getOrderBy();
    if (orderBy != null) {
      if (!WorkspaceListingConfig.PERMITTED_ORDERBY_FIELDS.contains(orderBy)) {
        errors.reject(
            "errors.invalidorderbyclause",
            new Object[] {orderBy, join(WorkspaceListingConfig.PERMITTED_ORDERBY_FIELDS, ",")},
            null);
      }
    }
  }

  private boolean allOptionsAreRecords(String[] options) {
    return Arrays.stream(options).allMatch(SearchConstants.RECORDS_SEARCH_OPTION::equals);
  }

  /**
   * Check if the date isn't an edge-case and should be attempted to be parsed as a date object
   *
   * @param date string to check
   * @return true if date isn't empty or "null"
   */
  private boolean shouldParseDate(String date) {
    return !date.isEmpty() && !date.equals("null");
  }
}
