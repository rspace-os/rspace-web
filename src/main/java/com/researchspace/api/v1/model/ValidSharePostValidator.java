package com.researchspace.api.v1.model;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.apache.commons.collections4.CollectionUtils;

/** Class-level validator for input to /share POST */
public class ValidSharePostValidator implements ConstraintValidator<ValidSharePost, SharePost> {

  @Override
  public void initialize(ValidSharePost constraintAnnotation) {}

  @Override
  public boolean isValid(SharePost post, ConstraintValidatorContext context) {
    return atLeastOneUserOrGroup(post);
  }

  private boolean atLeastOneUserOrGroup(SharePost post) {
    return groupShareHasItem(post) || userShareHasItem(post);
  }

  private boolean userShareHasItem(SharePost post) {
    return !CollectionUtils.isEmpty(post.getUserSharePostItems());
  }

  private boolean groupShareHasItem(SharePost post) {
    return !CollectionUtils.isEmpty(post.getGroupSharePostItems());
  }
}
