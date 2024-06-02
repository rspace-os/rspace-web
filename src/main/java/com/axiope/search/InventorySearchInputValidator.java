package com.axiope.search;

import static com.axiope.search.SearchConstants.INVENTORY_SEARCH_OPTION;

import com.researchspace.model.User;
import org.springframework.validation.Errors;

/**
 * Validator for a inventory search configuration object.<br>
 * A new object should be made for each validation, as this object contains user state.
 */
public class InventorySearchInputValidator extends SearchInputValidator {

  public InventorySearchInputValidator(User subject) {
    super(subject);
  }

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(SearchConfig.class);
  }

  /**
   * Validates search query
   *
   * @param target WorkspaceListingConfig to validate
   * @param errors object containing errors if any constraint fails
   */
  @Override
  public void validate(Object target, Errors errors) {
    SearchConfig input = (SearchConfig) target;
    String[] options = input.getOptions();
    String[] terms = input.getTerms();

    validateSearchOptions(options, terms, errors);
    if (errors.hasErrors()) {
      return;
    }

    // inventory-specific validation below

    for (int i = 0; i < terms.length; i++) {
      if (options[i].equals(INVENTORY_SEARCH_OPTION)) {
        if (terms[i].startsWith("*") || terms[i].startsWith("?")) {
          errors.reject("errors.textquerywildcardstartdisallowed", new String[] {terms[i]}, null);
          return;
        }
      }
    }
  }
}
