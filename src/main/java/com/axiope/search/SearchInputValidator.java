package com.axiope.search;

import static com.axiope.search.SearchConstants.CREATION_DATE_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.INVENTORY_PARENT_ID_OPTION;
import static com.axiope.search.SearchConstants.INVENTORY_PARENT_SAMPLE_ID_OPTION;
import static com.axiope.search.SearchConstants.INVENTORY_PARENT_TEMPLATE_ID_OPTION;
import static com.axiope.search.SearchConstants.MAX_TERM_SIZE;
import static com.axiope.search.SearchConstants.MODIFICATION_DATE_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.OWNER_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.RECORDS_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.SEARCH_OPTIONS_LIST;
import static com.axiope.search.SearchConstants.TAG_SEARCH_OPTION;
import static org.apache.commons.lang.StringUtils.isBlank;

import com.researchspace.model.Role;
import com.researchspace.model.User;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Abstract class with common validation code for RSpace searches.<br>
 * A new object should be made for each validation, as this object stores user in its state.
 */
public abstract class SearchInputValidator implements Validator {

  private static final int MIN_SYSADMIN_TERM_LENGTH = 5;
  private static final int MIN_REGULAR_USER_TERM_LENGTH = 2;

  private User user;

  public SearchInputValidator(User subject) {
    this.user = subject;
  }

  private Pattern LUCENE_TOO_PERMISSIVE_WILDCARD = Pattern.compile(":\\*");

  protected boolean isTooPermissiveLucene(String term) {
    if (term.trim().startsWith(SearchConstants.NATIVE_LUCENE_PREFIX)) {
      Matcher m = LUCENE_TOO_PERMISSIVE_WILDCARD.matcher(term);
      return m.find();
    }
    return false;
  }

  /** Checks search terms validation rules common for all RSpace lucene searches. */
  protected void validateSearchOptions(String[] options, String[] terms, Errors errors) {
    if (options.length == 0) {
      errors.reject("errors.nooptions");
    }
    for (String option : options) {
      if (isBlank(option)) {
        errors.reject("errors.searchoptionblank");
        return;
      }
      if (!SEARCH_OPTIONS_LIST.contains(option)) {
        errors.reject("errors.unknownsearchoption", new String[] {option}, null);
        return;
      }
    }

    if (terms.length == 0) {
      errors.reject("errors.noterms");
      return;
    }
    if (terms.length != options.length) {
      errors.reject(
          "errors.termsoptionsmismatch", new Object[] {terms.length, options.length}, null);
      return;
    }

    for (int i = 0; i < terms.length; i++) {
      if (isBlank(terms[i])) {
        errors.reject("errors.searchtermblank");
        return;
      }
      if (terms[i].length() > MAX_TERM_SIZE) {
        errors.reject(
            "errors.searchtermtoolong", new Object[] {terms[i].length(), MAX_TERM_SIZE, i}, null);
        return;
      }
      if (isTooPermissiveLucene(terms[i])) {
        errors.reject("errors.termtoopermissive", new String[] {terms[i]}, null);
      }
    }

    if (!errors.hasErrors()) {
      validateSearchTerm(terms, options, errors);
    }
  }

  protected void validateSearchTerm(String[] terms, String[] options, Errors errors) {
    searchTermLongEnough(terms, options, errors);
    for (String term : terms) {
      if (isSysAdminWithWildcards(term)) {
        errors.reject("errors.nowildcards", new Object[] {term}, null);
        return;
      }
    }
  }

  /**
   * Makes sure that search terms that could yield too many results are limited. This is but a
   * heuristic, and should be removed in the future when search becomes faster / streamed (limited
   * to a number of results) properly.
   */
  protected void searchTermLongEnough(String[] terms, String[] options, Errors errors) {
    for (int i = 0; i < terms.length; ++i) {
      if (shouldSkipLengthValidationOfTerm(options[i])) continue;

      // If searching for the owner of a file, we will allow it to be less than 5 chars (min 2)
      if (user.hasRole(Role.SYSTEM_ROLE) && !options[i].equals(OWNER_SEARCH_OPTION)) {
        if (terms[i].length() < MIN_SYSADMIN_TERM_LENGTH) {
          errors.reject(
              "errors.searchtermminlength", new Object[] {MIN_SYSADMIN_TERM_LENGTH}, null);
          return;
        }

      } else if (terms[i].length() < MIN_REGULAR_USER_TERM_LENGTH) {
        errors.reject(
            "errors.searchtermminlength", new Object[] {MIN_REGULAR_USER_TERM_LENGTH}, null);
        return;
      }
    }
  }

  /**
   * Checks if this term's data shouldn't be validated for length
   *
   * @param option name of the term
   * @return true if the term's length shouldn't matter
   */
  private boolean shouldSkipLengthValidationOfTerm(String option) {
    return option.equals(RECORDS_SEARCH_OPTION)
        || option.equals(CREATION_DATE_SEARCH_OPTION)
        || option.equals(MODIFICATION_DATE_SEARCH_OPTION)
        || option.equals(INVENTORY_PARENT_ID_OPTION)
        || option.equals(INVENTORY_PARENT_TEMPLATE_ID_OPTION)
        || option.equals(INVENTORY_PARENT_SAMPLE_ID_OPTION)
        || (user.hasRole(Role.SYSTEM_ROLE) && option.equals(TAG_SEARCH_OPTION));
    // Sysadmins can do short tag searches :)
  }

  protected boolean isSysAdminWithWildcards(String term) {
    return user.hasRole(Role.SYSTEM_ROLE) && StringUtils.containsAny(term, "*?");
  }
}
