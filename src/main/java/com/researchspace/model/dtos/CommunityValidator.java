package com.researchspace.model.dtos;

import com.researchspace.model.Community;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Validates user input of a community object from createCommunity form */
@Component("communityValidator")
public class CommunityValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(Community.class);
  }

  @Override
  public void validate(Object target, Errors errors) {
    Community community = (Community) target;

    if (StringUtils.isBlank(community.getDisplayName())) {
      errors.rejectValue("displayName", "errors.required", new Object[] {"Display name"}, null);
    }
    if (StringUtils.isBlank(community.getUniqueName())) {
      errors.rejectValue("uniqueName", "errors.required", new Object[] {"Unique name"}, null);
    }
    if (community.getAdminIds() == null || community.getAdminIds().isEmpty()) {
      errors.rejectValue(
          "adminIds", "errors.required", new Object[] {"Choosing an administrator"}, null);
    }
  }
}
