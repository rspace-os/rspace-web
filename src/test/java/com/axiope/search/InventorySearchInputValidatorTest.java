package com.axiope.search;

import static com.axiope.search.SearchConstants.FULL_TEXT_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.OWNER_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.TAG_SEARCH_OPTION;
import static com.researchspace.Constants.SYSADMIN_ROLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class InventorySearchInputValidatorTest {

  InventorySearchInputValidator validator;
  SearchConfig input;

  String[] OKOPTION = {SearchConstants.INVENTORY_SEARCH_OPTION};
  String[] UUNKNOWNOPTION = {"xxx"};
  String[] OKTERM = {"name"};
  String[] BLANKTERM = {" "};
  String[] LEADING_WILDCARDTERM = {"*text"};
  String[] TOO_LONG_SEARCHTERM = {
    RandomStringUtils.randomAlphabetic(SearchConstants.MAX_TERM_SIZE + 1)
  };
  String[] OKTERMLENGHT2 = {"name", "name2"};
  String[] OKLUCENE = {"l: xxx:xxx OR aaa:bbb"};
  String[] TOO_WILDCARD = {"l: xxx:xxx OR aaa:*"};

  String[] PARENTID_OPTION = {SearchConstants.INVENTORY_PARENT_ID_OPTION};
  String[] PARENTTEMPLATEID_OPTION = {SearchConstants.INVENTORY_PARENT_TEMPLATE_ID_OPTION};
  String[] PARENTSAMPLEID_OPTION = {SearchConstants.INVENTORY_PARENT_SAMPLE_ID_OPTION};
  String[] SHORT_ID_TERM = {"5"};

  User user;

  @Before
  public void setUp() throws Exception {
    user = TestFactory.createAnyUser("any");
    input = new WorkspaceSearchConfig();
    validator = new InventorySearchInputValidator(user);
  }

  @Test
  public void testSupports() {
    assertTrue(validator.supports(SearchConfig.class));
  }

  @Test
  public void testValidate() {
    Errors errors = reinitializeErrors();
    validator.validate(input, errors);

    assertTrue(errors.hasGlobalErrors());
    // no input data
    assertEquals(2, errors.getErrorCount());

    // ok
    errors = reinitializeErrors();
    input = createSearchConfig(user, OKOPTION, OKTERM);
    validator.validate(input, errors);
    assertFalse(errors.hasGlobalErrors());

    // 1-digit parentId or parentSampleId search is ok
    errors = reinitializeErrors();
    input = createSearchConfig(user, PARENTID_OPTION, SHORT_ID_TERM);
    validator.validate(input, errors);
    input = createSearchConfig(user, PARENTTEMPLATEID_OPTION, SHORT_ID_TERM);
    validator.validate(input, errors);
    input = createSearchConfig(user, PARENTSAMPLEID_OPTION, SHORT_ID_TERM);
    validator.validate(input, errors);
    assertFalse(errors.hasGlobalErrors());

    // more terms than options
    errors = reinitializeErrors();
    input = createSearchConfig(user, OKOPTION, OKTERMLENGHT2);
    validator.validate(input, errors);
    assertTrue(errors.hasGlobalErrors());
    assertEquals(1, errors.getErrorCount());
    assertEquals("errors.termsoptionsmismatch", errors.getAllErrors().get(0).getCode());

    // unknown search option
    errors = reinitializeErrors();
    input = createSearchConfig(user, UUNKNOWNOPTION, OKTERM);
    validator.validate(input, errors);
    assertTrue(errors.hasGlobalErrors());
    assertEquals(1, errors.getErrorCount());
    assertEquals("errors.unknownsearchoption", errors.getAllErrors().get(0).getCode());

    // empty search term
    errors = reinitializeErrors();
    input = createSearchConfig(user, OKOPTION, BLANKTERM);
    validator.validate(input, errors);
    assertTrue(errors.hasGlobalErrors());
    assertEquals(1, errors.getErrorCount());
    assertEquals("errors.searchtermblank", errors.getAllErrors().get(0).getCode());

    // too long search term
    errors = reinitializeErrors();
    input = createSearchConfig(user, OKOPTION, TOO_LONG_SEARCHTERM);
    validator.validate(input, errors);
    assertTrue(errors.hasGlobalErrors());
    assertEquals(1, errors.getErrorCount());
    assertEquals("errors.searchtermtoolong", errors.getAllErrors().get(0).getCode());
  }

  private SearchConfig createSearchConfig(User user, String[] options, String[] terms) {
    SearchConfig config = new WorkspaceSearchConfig(user);
    config.setOptions(options);
    config.setTerms(terms);
    return config;
  }

  private BeanPropertyBindingResult reinitializeErrors() {
    return new BeanPropertyBindingResult(input, "MyObject");
  }

  @Test
  public void searchTermMinimumLengthAndSysadminRestrictions() {
    Errors errors = reinitializeErrors();
    User sysadmin = TestFactory.createAnyUserWithRole("sys", SYSADMIN_ROLE);

    // too short
    SearchConfig cfg =
        createSearchConfig(sysadmin, new String[] {TAG_SEARCH_OPTION}, new String[] {"a"});
    validator.validate(cfg, errors);
    assertTrue(errors.hasGlobalErrors());

    // sysadmin must be >=5 chars RSPAC-907 unless tag option RSPAC-468
    cfg =
        createSearchConfig(sysadmin, new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"a234"});
    validator = new InventorySearchInputValidator(sysadmin);
    validator.validate(cfg, errors);
    assertTrue(errors.hasGlobalErrors());
    cfg = createSearchConfig(sysadmin, new String[] {TAG_SEARCH_OPTION}, new String[] {"a234"});
    errors = reinitializeErrors();
    validator.validate(cfg, errors);
    assertFalse("Tag option fails with small tag value", errors.hasGlobalErrors());

    // unless it's a user (owner) search, where short names are fine (as long as more than 1 char)
    cfg = createSearchConfig(sysadmin, new String[] {OWNER_SEARCH_OPTION}, new String[] {"dude"});
    validator = new InventorySearchInputValidator(sysadmin);
    validator.validate(cfg, errors);
    assertFalse(errors.hasGlobalErrors());
    cfg = createSearchConfig(sysadmin, new String[] {OWNER_SEARCH_OPTION}, new String[] {"G"});
    validator = new InventorySearchInputValidator(sysadmin);
    validator.validate(cfg, errors);
    assertTrue(errors.hasGlobalErrors());

    // .. and no wildcards RSPAC-907
    cfg = createSearchConfig(sysadmin, new String[] {TAG_SEARCH_OPTION}, new String[] {"a2345*"});
    validator.validate(cfg, errors);
    assertTrue(errors.hasGlobalErrors());
  }
}
