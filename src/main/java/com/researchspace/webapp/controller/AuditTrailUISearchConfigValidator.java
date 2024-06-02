package com.researchspace.webapp.controller;

import com.researchspace.model.views.CommunityListResult;
import com.researchspace.model.views.GroupListResult;
import com.researchspace.service.audit.search.AbstractAuditSrchConfigValidator;
import org.springframework.validation.Errors;

public class AuditTrailUISearchConfigValidator extends AbstractAuditSrchConfigValidator {

  @Override
  public boolean supports(Class<?> clazz) {
    return AuditTrailUISearchConfig.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    AuditTrailUISearchConfig config = (AuditTrailUISearchConfig) target;
    super.validate(target, errors);
    String groups = config.getGroups();
    String community = config.getCommunities();
    if (groups != null
        && (groups.length() < 2 || !GroupListResult.validateMultiGroupAutocompleteInput(groups))) {
      errors.rejectValue("groups", "errors.invalid", new Object[] {groups}, null);
    } else if (community != null
        && (community.length() < 2
            || !CommunityListResult.validateMultiCommunityAutocompleteInput(community))) {
      errors.rejectValue("communities", "errors.invalid", new Object[] {community}, null);
    }
  }
}
