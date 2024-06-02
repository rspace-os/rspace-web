package com.researchspace.model.dtos;

import com.researchspace.model.User;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

@Component("CreateCloudGroupValidator")
public class CreateCloudGroupValidator implements Validator {

  private Matcher matcher;

  /** Valid Email pattern for creating new group */
  public static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\\\<>' \"]+@[^\\\\<>' \"]+$");

  private static final int EMAIL_LENGTH = 255;

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(CreateCloudGroup.class);
  }

  @Override
  public void validate(Object target, Errors errors) {

    CreateCloudGroup createCloudGroup = (CreateCloudGroup) target;
    User userInSession = createCloudGroup.getSessionUser();
    ValidationUtils.rejectIfEmptyOrWhitespace(
        errors, "groupName", "groupName.required", "Group name is required");
    // someone else will be PI, i.e a nomination
    ValidationUtils.rejectIfEmptyOrWhitespace(
        errors, "piEmail", "principalEmail.required", "Principal investigator email is required");

    if (!isEmailValid(createCloudGroup.getPiEmail())) {
      errors.rejectValue("piEmail", "principalEmail.incorrect", "Please enter a correct email");
      return;
    }

    List<String> listEmails = Arrays.asList(createCloudGroup.getEmails());
    if (!listEmails.isEmpty() && !listEmails.get(0).isEmpty()) {
      for (String email : listEmails) {

        if (!isEmailValid(email)) {
          errors.rejectValue("emails", "emails.incorrect", "Enter correct emails");
          return;
        }

        if (userInSession.getEmail().equals(email)) {
          errors.rejectValue(
              "emails",
              "emails.invalid",
              "You are already in the group. Please check the email addresses.");
          return;
        }
      }
    }
  }

  private boolean isEmailValid(String email) {
    if (StringUtils.isBlank(email)) {
      return false;
    }
    matcher = EMAIL_PATTERN.matcher(email);
    return matcher.matches() && email.length() < EMAIL_LENGTH;
  }
}
