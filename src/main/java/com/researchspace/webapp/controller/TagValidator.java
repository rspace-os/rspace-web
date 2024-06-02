package com.researchspace.webapp.controller;

import java.util.Arrays;
import java.util.List;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class TagValidator implements Validator {

  static final List<Character> FORBIDDEN_TAG_CHARACTERS = Arrays.asList('<', '>', '/', '\\');

  @Override
  public boolean supports(Class<?> clazz) {
    return RSpaceTag.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    RSpaceTag tag = (RSpaceTag) target;
    // empty is OK, RSPAC-198, we might be clearing tags
    if (tag.getTag() != null) {
      if (tag.getTag().length() == 1) {
        errors.reject("errors.minlength", new Object[] {"Tag", 2}, "Tag is too short");
      }
      for (char forbidden : FORBIDDEN_TAG_CHARACTERS) {
        if (tag.getTag().indexOf(forbidden) >= 0) {
          errors.reject(
              "errors.invalidchars", new Object[] {forbidden, "tag"}, "Invalid characters");
        }
      }
    }
  }
}
