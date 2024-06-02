package com.researchspace.api.v1.model;

import java.util.regex.Pattern;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class ValidRoRID implements ConstraintValidator<ValidRoR, String> {
  public static final String RSPACE_ROR_FORWARD_SLASH_DELIM = "__rspacror_forsl__";
  private static final Pattern toMatch =
      Pattern.compile("^0[a-hj-km-np-tv-z|0-9]{6}[0-9]{2}$", Pattern.DOTALL);

  @Override
  public void initialize(ValidRoR validRoR) {}

  @Override
  public boolean isValid(String rorID, ConstraintValidatorContext constraintValidatorContext) {
    String searchTerm = rorID.replaceAll(RSPACE_ROR_FORWARD_SLASH_DELIM, "/");
    if (toMatch
        .matcher(searchTerm.replaceAll("https://", "").replaceAll("ror.org/", ""))
        .matches()) {
      return true;
    } else {
      constraintValidatorContext.disableDefaultConstraintViolation();
      constraintValidatorContext
          .buildConstraintViolationWithTemplate(searchTerm + " is not a valid ROR")
          .addConstraintViolation();
      return false;
    }
  }
}
