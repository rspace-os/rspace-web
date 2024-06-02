package com.researchspace.model.dtos;

import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class SwapPiValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return SwapPiCommand.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    SwapPiCommand cmd = (SwapPiCommand) target;
    User newPi = cmd.getNewPi();
    Group grp = cmd.getGroup();
    User currPi = cmd.getCurrPi();
    if (!GroupType.LAB_GROUP.equals(grp.getGroupType())) {
      errors.reject("group.swapPi.labGroupOny.err", new Object[] {grp.getDisplayName()}, null);
    }
    if (!newPi.hasRole(Role.PI_ROLE)) {
      errors.reject("group.swapPi.requiredPI.role", new Object[] {}, null);
    }
    if (!grp.getAllNonPIMembers().contains(newPi)) {
      errors.reject("group.swapPi.requiredPI.inGroup", new Object[] {}, null);
    }
    if (currPi.equals(newPi)) {
      errors.reject("group.swapPi.requiredNewPI", new Object[] {}, null);
    }
  }
}
