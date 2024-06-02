package com.researchspace.api.v1.model;

import com.researchspace.model.User;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Data required to create new user account. Basic validation for mandatory fields.
 *
 * <p>Restricted to creation of User or PIs
 */
@Data
@Builder
public class ApiUserPost {

  ApiUserPost(
      String username,
      String firstName,
      String lastName,
      String email,
      String password,
      String role,
      String organisation,
      boolean generateApiKey) {
    super();
    this.username = username;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.password = password;
    this.role = role;
    this.organisation = organisation;
    this.generateApiKey = generateApiKey;
  }

  /** For Spring */
  public ApiUserPost() {
    super();
  }

  @NotNull
  @Size(min = User.MIN_UNAME_LENGTH, max = User.MAX_UNAME_LENGTH)
  @Pattern(regexp = User.ALLOWED_USERNAME_CHARS_REGEX)
  private String username;

  @NotBlank private String firstName;

  @NotBlank private String lastName;

  @Email @NotNull private String email;

  @NotEmpty
  @Size(min = User.MIN_PWD_LENGTH, max = User.MAX_UNAME_LENGTH)
  @Pattern(regexp = User.ALLOWED_PWD_CHARS_REGEX)
  private String password;

  /** Default is user role if not set */
  @Pattern(
      regexp = "(ROLE_USER)|(ROLE_PI)",
      message = "Unknown role - must be one of 'ROLE_USER' or 'ROLE_PI'")
  @NotNull
  @Builder.Default
  private String role = "ROLE_USER";

  // only required for Community user
  private String organisation;

  /** Whether or not the created user should have a key created. */
  private boolean generateApiKey;
}
