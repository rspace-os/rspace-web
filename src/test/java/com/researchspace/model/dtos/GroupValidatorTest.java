package com.researchspace.model.dtos;

import static com.researchspace.model.Group.GROUP_UNIQUE_NAME_SUFFIX_LENGTH;
import static com.researchspace.model.Organisation.MAX_INDEXABLE_UTF_LENGTH;
import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.dao.GroupDao;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class GroupValidatorTest extends SpringTransactionalTest {

  @Autowired private GroupDao grpDao;

  @Autowired
  @Qualifier(value = "grpValidator")
  private Validator validator;

  private User owner;

  @Before
  public void setUp() throws Exception {
    owner = TestFactory.createAnyUser("owner");
  }

  @Test
  public void testValidateOK() {
    Group OK = new Group("newname", owner);
    OK.setMemberString(getListofMembers("a", "b"));
    OK.setPis("a");
    OK.setDisplayName("a display name");
    Errors errors = new BeanPropertyBindingResult(OK, "MyObject");
    validator.validate(OK, errors);
    assertFalse(errors.hasErrors());
  }

  @Test
  public void testValidateAlphanumericGroupNameOnly() {
    Group notOk = new Group("!!!", owner);
    Errors errors = new BeanPropertyBindingResult(notOk, "MyObject");
    validator.validate(notOk, errors);
    assertTrue(errors.hasErrors());
    assertTrue(errors.hasFieldErrors("displayName"));

    Group notOk2 = new Group("Rob's Group", owner);
    Errors errors2 = new BeanPropertyBindingResult(notOk, "MyObject");
    validator.validate(notOk2, errors2);
    assertTrue(errors2.hasErrors());
    assertTrue(errors2.hasFieldErrors("uniqueName"));
  }

  @Test
  public void testValidateGroupNameLength() {
    // edge case ok
    Group notOk =
        new Group(
            randomAlphabetic(MAX_INDEXABLE_UTF_LENGTH - GROUP_UNIQUE_NAME_SUFFIX_LENGTH), owner);
    notOk.setMemberString(getListofMembers("a", "b"));
    notOk.setPis("a");
    notOk.setDisplayName("a display name");
    Errors errors = new BeanPropertyBindingResult(notOk, "MyObject");
    validator.validate(notOk, errors);
    assertFalse(errors.hasErrors());
    notOk.setUniqueName(
        randomAlphabetic(MAX_INDEXABLE_UTF_LENGTH - GROUP_UNIQUE_NAME_SUFFIX_LENGTH + 1));
    errors = new BeanPropertyBindingResult(notOk, "MyObject");
    validator.validate(notOk, errors);
    assertTrue(errors.hasErrors());
    assertTrue(errors.hasFieldErrors("uniqueName"));
  }

  @Test
  public void testValidateNonUniqueGroupNameNotOK() {
    Group repeatName = new Group("g1", owner);
    repeatName.setDisplayName("dname");
    grpDao.save(repeatName);

    Group repeatName2 = new Group("g1", owner);
    // can't create new group
    Errors errors2 = new BeanPropertyBindingResult(repeatName2, "MyObject");
    repeatName2.setDisplayName("dname");
    validator.validate(repeatName2, errors2);
    assertFalse(errors2.hasFieldErrors("displayName"));

    // but can validate a persisted group with
    // can't create new group
    Errors errors3 = new BeanPropertyBindingResult(repeatName, "MyObject");
    validator.validate(repeatName, errors3);
    assertFalse(
        ValidationTestUtils.hasError(GroupValidator.GROUP_MEMBERS_NON_UNIQUE_NAME, errors3));
  }

  @Test
  public void testValidateNullMemberListNotOK() {
    Group grp = new Group("name", owner);
    grp.setMemberString(null);
    Errors errors = new BeanPropertyBindingResult(grp, "MyObject");
    validator.validate(grp, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError(GroupValidator.GROUP_MEMBERS_NONESELECTED, errors));

    grp.setMemberString(Collections.EMPTY_LIST);
    errors = new BeanPropertyBindingResult(grp, "MyObject");
    validator.validate(grp, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError(GroupValidator.GROUP_MEMBERS_NONESELECTED, errors));
  }

  @Test
  public void testValidateMissingPINotOK() {
    Group grp = new Group("name", owner);
    grp.setMemberString(getListofMembers("a", "b", "c"));
    Errors errors = new BeanPropertyBindingResult(grp, "MyObject");
    validator.validate(grp, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError(GroupValidator.PI_NOT_SELECTED, errors));
  }

  @Test
  public void testValidateProjectGroupDoesNotNeedPI() {
    Group grp = new Group("name", owner);
    grp.setGroupType(GroupType.PROJECT_GROUP);
    grp.setMemberString(getListofMembers("a", "b", "c"));
    grp.setGroupOwners("c");
    grp.setDisplayName("a display name");
    Errors errors = new BeanPropertyBindingResult(grp, "MyObject");
    validator.validate(grp, errors);
    System.out.println(errors);
    assertFalse(errors.hasErrors());
  }

  @Test
  public void testValidateProjectGroupNeedsOwner() {
    Group grp = new Group("name", owner);
    grp.setGroupType(GroupType.PROJECT_GROUP);
    grp.setMemberString(getListofMembers("a", "b", "c"));
    grp.setDisplayName("a display name");
    Errors errors = new BeanPropertyBindingResult(grp, "MyObject");
    validator.validate(grp, errors);
    System.out.println(errors);
    assertTrue(ValidationTestUtils.hasError(GroupValidator.GROUP_OWNER_NOT_SELECTED, errors));
  }

  @Test
  public void testValidatePINotInListNotOK() {
    Group grp = new Group("name", owner);
    grp.setMemberString(getListofMembers("a", "b", "c"));
    grp.setPis("X");
    Errors errors = new BeanPropertyBindingResult(grp, "MyObject");
    validator.validate(grp, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError(GroupValidator.PI_NOT_IN_GROUP, errors));
  }

  @Test
  public void testValidateOwnerNotInListNotOK() {
    Group grp = new Group("name", owner);
    grp.setMemberString(getListofMembers("a", "b", "c"));
    grp.setGroupOwners("X");
    Errors errors = new BeanPropertyBindingResult(grp, "MyObject");
    validator.validate(grp, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError(GroupValidator.GROUP_OWNER_NOT_IN_GROUP, errors));
  }

  @Test
  public void testValidateAdminNotInListNotOK() {
    Group grp = new Group("name", owner);
    grp.setMemberString(getListofMembers("a", "b", "c"));
    grp.setPis("a");
    String NONMEMBER = "XX";
    grp.setAdmins(NONMEMBER);
    Errors errors = new BeanPropertyBindingResult(grp, "MyObject");
    validator.validate(grp, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError(GroupValidator.ADMIN_NOT_IN_GROUP, errors));
  }

  private List<String> getListofMembers(String... names) {
    return Arrays.asList(names);
  }
}
