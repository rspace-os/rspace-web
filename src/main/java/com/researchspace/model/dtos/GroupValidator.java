package com.researchspace.model.dtos;

import static org.apache.commons.lang.StringUtils.isAlphanumeric;
import static org.apache.commons.lang.StringUtils.isBlank;

import com.researchspace.model.Group;
import com.researchspace.model.Organisation;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Validates group information supplied from the UI. */
@Component("grpValidator")
public class GroupValidator implements Validator {

  // actual string stored in property file
  public static final String GROUP_MEMBERS_NONESELECTED = "group.members.noneselected";
  public static final String GROUP_MEMBERS_NON_UNIQUE_NAME = "group.members.non_unique_name";
  public static final String PI_NOT_IN_GROUP = "group.members.pi_not_group";
  public static final String ADMIN_NOT_IN_GROUP = "group.members.admin_not_group";
  public static final String PI_NOT_SELECTED = "group.members.nopi";
  public static final String GROUP_OWNER_NOT_SELECTED = "group.members.no_group_owner";
  public static final String GROUP_OWNER_NOT_IN_GROUP = "group.members.group_owner_not_in_group";

  @Override
  public boolean supports(Class<?> clazz) {
    return Group.class.isAssignableFrom(clazz);
  }

  /**
   *
   *
   * <ul>
   *   <li>Form fields must not be empty;
   *   <li>group name must be unique + and alphanumeric.
   * </ul>
   */
  @Override
  public void validate(Object target, Errors errors) {

    Group group = (Group) target;

    if (isBlank(group.getDisplayName())) {
      errors.rejectValue("displayName", "group.emptyname", null, null);
    }
    if (!isBlank(group.getUniqueName()) && !isAlphanumeric(group.getUniqueName())) {
      errors.rejectValue("uniqueName", "group.invalidcharacters", null, null);
    }
    if (!isBlank(group.getUniqueName())
        && Organisation.MAX_INDEXABLE_UTF_LENGTH
            < group.getUniqueName().length() + Group.GROUP_UNIQUE_NAME_SUFFIX_LENGTH) {
      errors.rejectValue(
          "uniqueName",
          "errors.maxlength",
          new String[] {"group name", "" + Organisation.MAX_INDEXABLE_UTF_LENGTH},
          null);
    }
    if (group.getMemberString() == null || group.getMemberString().isEmpty()) {
      errors.rejectValue("memberString", GROUP_MEMBERS_NONESELECTED, null, null);
    }

    if (!group.isProjectGroup() && isBlank(group.getPis())) {
      errors.rejectValue("pis", PI_NOT_SELECTED, null, null);
    }
    // check for owners in project group
    if (group.isProjectGroup() && isBlank(group.getGroupOwners())) {
      errors.rejectValue("groupOwners", GROUP_OWNER_NOT_SELECTED, null, null);
    }

    // check pi name is in group
    if (!isBlank(group.getPis()) && group.getMemberString() != null) {
      String[] members = group.getPis().split(",");
      for (String member : members) {
        if (!group.getMemberString().contains(member.trim())) {
          errors.rejectValue("pis", PI_NOT_IN_GROUP, new Object[] {member}, null);
        }
      }
    }
    // check any admin name is in group
    if (!StringUtils.isBlank(group.getAdmins()) && group.getMemberString() != null) {
      String[] members = group.getAdmins().split(",");
      for (String member : members) {
        if (!group.getMemberString().contains(member.trim())) {
          errors.rejectValue("pis", ADMIN_NOT_IN_GROUP, new Object[] {member}, null);
        }
      }
    }
    // check any group owner name is in group
    if (!StringUtils.isBlank(group.getGroupOwners()) && group.getMemberString() != null) {
      String[] members = group.getGroupOwners().split(",");
      for (String member : members) {
        if (!group.getMemberString().contains(member.trim())) {
          errors.rejectValue("groupOwners", GROUP_OWNER_NOT_IN_GROUP, new Object[] {member}, null);
        }
      }
    }
  }
}
