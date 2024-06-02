package com.axiope.search;

import static com.axiope.search.SearchConstants.*;
import static com.researchspace.Constants.SYSADMIN_ROLE;
import static com.researchspace.testutils.SearchTestUtils.createAdvSearchCfg;
import static org.junit.Assert.*;

import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.TestFactory;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class WorkspaceSearchInputValidatorTest {
  WorkspaceSearchInputValidator validator;
  WorkspaceListingConfig input;
  PaginationCriteria<BaseRecord> crit = PaginationCriteria.createDefaultForClass(BaseRecord.class);
  String[] OKOPTION = {SearchConstants.NAME_SEARCH_OPTION};
  String[] UUNKNOWNOPTION = {"xxx"};
  String[] OKTERM = {"name"};
  String[] LEADING_WILDCARDTERM = {"*text"};
  String[] TOO_LONG_SEARCHTERM = {
    RandomStringUtils.randomAlphabetic(SearchConstants.MAX_TERM_SIZE + 1)
  };
  String[] OKTERMLENGHT2 = {"name", "name2"};
  String[] FULLTEXTOPTION = {FULL_TEXT_SEARCH_OPTION};
  String[] GLOBALOPTION = {ALL_SEARCH_OPTION};
  String[] OKLUCENE = {"l: xxx:xxx OR aaa:bbb"};
  String[] TOO_WILDCARD = {"l: xxx:xxx OR aaa:*"};
  User user;

  @Before
  public void setUp() throws Exception {
    user = TestFactory.createAnyUser("any");
    input = new WorkspaceListingConfig();
    validator = createValidator(user);
  }

  private WorkspaceSearchInputValidator createValidator(User user2) {
    return new WorkspaceSearchInputValidator(user2);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testSupports() {
    assertTrue(validator.supports(WorkspaceListingConfig.class));
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
    input = new WorkspaceListingConfig(crit, OKOPTION, OKTERM, -1L, true);
    validator.validate(input, errors);
    assertFalse(errors.hasGlobalErrors());

    // more terms than options
    errors = reinitializeErrors();
    input = new WorkspaceListingConfig(crit, OKOPTION, OKTERMLENGHT2, -1L, true);
    validator.validate(input, errors);
    assertTrue(errors.hasGlobalErrors());
    assertEquals(1, errors.getErrorCount());
    assertEquals("errors.termsoptionsmismatch", errors.getAllErrors().get(0).getCode());

    // unknown search option
    errors = reinitializeErrors();
    input = new WorkspaceListingConfig(crit, UUNKNOWNOPTION, OKTERM, -1L, true);
    validator.validate(input, errors);
    assertTrue(errors.hasGlobalErrors());
    assertEquals(1, errors.getErrorCount());
    assertEquals("errors.unknownsearchoption", errors.getAllErrors().get(0).getCode());

    // too long search term
    errors = reinitializeErrors();
    input = new WorkspaceListingConfig(crit, OKOPTION, TOO_LONG_SEARCHTERM, -1L, true);
    validator.validate(input, errors);
    assertTrue(errors.hasGlobalErrors());
    assertEquals(1, errors.getErrorCount());
    assertEquals("errors.searchtermtoolong", errors.getAllErrors().get(0).getCode());

    // unknown rderby clause
    errors = reinitializeErrors();
    input = new WorkspaceListingConfig(crit, OKOPTION, OKTERM, -1L, true);
    crit.setOrderBy("unknown");
    validator.validate(input, errors);
    assertTrue(errors.hasGlobalErrors());
    assertEquals(1, errors.getErrorCount());
    assertEquals("errors.invalidorderbyclause", errors.getAllErrors().get(0).getCode());

    errors = reinitializeErrors();
    input = new WorkspaceListingConfig(crit, FULLTEXTOPTION, LEADING_WILDCARDTERM, -1L, true);
    validator.validate(input, errors);
    assertTrue(errors.hasGlobalErrors());
    assertEquals(1, errors.getErrorCount());
    assertEquals("errors.textquerywildcardstartdisallowed", errors.getAllErrors().get(0).getCode());

    errors = reinitializeErrors();
    input = new WorkspaceListingConfig(crit, GLOBALOPTION, LEADING_WILDCARDTERM, -1L, true);
    validator.validate(input, errors);
    assertTrue(errors.hasGlobalErrors());
    assertEquals(1, errors.getErrorCount());
    assertEquals("errors.textquerywildcardstartdisallowed", errors.getAllErrors().get(0).getCode());

    errors = reinitializeErrors();
    input = new WorkspaceListingConfig(crit, FULLTEXTOPTION, OKLUCENE, -1L, true);
    validator.validate(input, errors);
    assertFalse(errors.hasGlobalErrors());

    // RSPAC-551 check wildcard search prevented
    errors = reinitializeErrors();
    input = new WorkspaceListingConfig(crit, FULLTEXTOPTION, TOO_WILDCARD, -1L, true);
    validator.validate(input, errors);
    assertTrue(errors.hasGlobalErrors());
    assertEquals("errors.termtoopermissive", errors.getAllErrors().get(0).getCode());
  }

  private BeanPropertyBindingResult reinitializeErrors() {
    crit = PaginationCriteria.createDefaultForClass(BaseRecord.class);
    return new BeanPropertyBindingResult(input, "MyObject");
  }

  @Test
  public void searchTermMinimumLengthAndSysadminRestrictions() {
    Errors errors = reinitializeErrors();

    // too short

    WorkspaceListingConfig cfg =
        createAdvSearchCfg(new String[] {TAG_SEARCH_OPTION}, new String[] {"a"});

    validator.validate(cfg, errors);
    assertTrue(errors.hasGlobalErrors());

    // sysadmin must be >=5 chars RSPAC-907 unless tag option RSPAC-468
    User sysadmin = TestFactory.createAnyUserWithRole("sys", SYSADMIN_ROLE);
    cfg = createAdvSearchCfg(new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"a234"});
    validator = createValidator(sysadmin);
    validator.validate(cfg, errors);
    assertTrue(errors.hasGlobalErrors());
    cfg = createAdvSearchCfg(new String[] {TAG_SEARCH_OPTION}, new String[] {"a234"});
    errors = reinitializeErrors();
    validator.validate(cfg, errors);
    assertFalse("Tag option fails with small tag value", errors.hasGlobalErrors());

    // unless it's a user (owner) search, where short names are fine (as long as more than 1 char)
    cfg = createAdvSearchCfg(new String[] {OWNER_SEARCH_OPTION}, new String[] {"dude"});
    validator = createValidator(sysadmin);
    validator.validate(cfg, errors);
    assertFalse(errors.hasGlobalErrors());
    cfg = createAdvSearchCfg(new String[] {OWNER_SEARCH_OPTION}, new String[] {"G"});
    validator = createValidator(sysadmin);
    validator.validate(cfg, errors);
    assertTrue(errors.hasGlobalErrors());

    // .. and no wildcards RSPAC-907
    cfg = createAdvSearchCfg(new String[] {TAG_SEARCH_OPTION}, new String[] {"a2345*"});
    validator.validate(cfg, errors);
    assertTrue(errors.hasGlobalErrors());
  }

  /** Folders and documents search options need to be a list of IDs (Longs separated by commas) */
  @Test
  public void invalidFolderAndDocumentTerms() {
    Errors errors;
    WorkspaceListingConfig cfg;

    // Validate that text doesn't pass
    cfg =
        createAdvSearchCfg(
            new String[] {RECORDS_SEARCH_OPTION, TAG_SEARCH_OPTION},
            new String[] {"folderName", "tag"});
    assertValidationProducesError(cfg);

    // Validate that a normal list passes with a term
    cfg =
        createAdvSearchCfg(
            new String[] {RECORDS_SEARCH_OPTION, TAG_SEARCH_OPTION},
            new String[] {"FL1234, SD1235,NB898  ,   NB9999", "tag"});
    assertValidationFindsNoErrors(cfg);

    // Validate that an invalid list doesn't pass
    cfg =
        createAdvSearchCfg(
            new String[] {RECORDS_SEARCH_OPTION, TAG_SEARCH_OPTION},
            new String[] {"SD1234, FLnice, NB9990", "tag"});
    assertValidationProducesError(cfg);

    // this isn't allowed either, must include a term that isn't a record selection
    cfg =
        createAdvSearchCfg(
            new String[] {RECORDS_SEARCH_OPTION, RECORDS_SEARCH_OPTION},
            new String[] {"SD123,SD456", "SD980"});
    errors = reinitializeErrors();
    validator.validate(cfg, errors);
    assertTrue(errors.hasGlobalErrors());
    assertEquals("errors.recordFilterMustIncludeTerm", errors.getGlobalError().getCode());
  }

  /**
   * Date range needs to be 2 dates, separated by a semicolon, either in ISO-8601 format or null,
   * now, or empty.
   */
  @Test
  public void invalidDateTerms() {
    WorkspaceListingConfig cfg;

    // validate that a normal date range parses / passes
    cfg =
        createAdvSearchCfg(
            new String[] {CREATION_DATE_SEARCH_OPTION, MODIFICATION_DATE_SEARCH_OPTION},
            new String[] {
              "2019-07-03T16:41:00.000Z;2019-07-06T16:41:00.000Z",
              "2019-07-03T16:41:00.000Z; 2020-09-15T15:53:00+05:00"
            });
    assertValidationFindsNoErrors(cfg);

    // validate that a date range till now passes
    cfg =
        createAdvSearchCfg(
            new String[] {CREATION_DATE_SEARCH_OPTION},
            new String[] {"2019-07-03T16:41:00.000Z;null"});
    assertValidationFindsNoErrors(cfg);

    // validate that the first term cannot be "now"
    cfg =
        createAdvSearchCfg(
            new String[] {CREATION_DATE_SEARCH_OPTION},
            new String[] {"now;2022-07-03T16:41:00.000-02:00"});
    assertValidationProducesError(cfg);

    // validate some more open ended intervals
    cfg = createAdvSearchCfg(new String[] {CREATION_DATE_SEARCH_OPTION}, new String[] {";"});
    assertValidationFindsNoErrors(cfg);

    cfg =
        createAdvSearchCfg(new String[] {CREATION_DATE_SEARCH_OPTION}, new String[] {"null;null"});
    assertValidationFindsNoErrors(cfg);

    // check that invalid formats get rejected
    cfg =
        createAdvSearchCfg(new String[] {CREATION_DATE_SEARCH_OPTION}, new String[] {"nice;null"});
    assertValidationProducesError(cfg);

    cfg =
        createAdvSearchCfg(
            new String[] {CREATION_DATE_SEARCH_OPTION}, new String[] {"9994949;now"});
    assertValidationProducesError(cfg);

    cfg =
        createAdvSearchCfg(
            new String[] {MODIFICATION_DATE_SEARCH_OPTION}, new String[] {"2019-09-09;null"});
    assertValidationProducesError(cfg);

    // requires correct format with timezone offset Or 'Z' for UTC
    cfg =
        createAdvSearchCfg(
            new String[] {MODIFICATION_DATE_SEARCH_OPTION}, new String[] {"2019-09-09T12:34:56;"});
    assertValidationProducesError(cfg);
  }

  private void assertValidationProducesError(WorkspaceListingConfig cfg) {
    Errors errors = reinitializeErrors();
    validator.validate(cfg, errors);
    assertTrue(errors.hasGlobalErrors());
  }

  private void assertValidationFindsNoErrors(WorkspaceListingConfig cfg) {
    Errors errors = reinitializeErrors();
    validator.validate(cfg, errors);
    assertFalse(errors.hasGlobalErrors());
  }
}
