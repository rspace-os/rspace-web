package com.researchspace.model.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * POJO for RepositorySubmission
 *
 * <p>Message properties are resolved in ValidationMessages.properties
 */
@Data
@NoArgsConstructor
public class RepoDepositMeta {
  protected static final int MAX_USERS = 10;
  protected static final int MAX_FIELD_LENGTH = 1000;
  protected static final int MIN_FIELD_LENGTH = 3;

  @Size(max = MAX_USERS, message = "{errors.collection.range} {authors}")
  @NotEmpty(message = "{authors} {errors.required.field}")
  private @Valid List<UserDepositorAdapter> authors;

  @Size(max = 10, message = "{errors.collection.range} {contacts}")
  @NotEmpty(message = "{contacts} {errors.required.field}")
  private @Valid List<UserDepositorAdapter> contacts;

  @Size(min = MIN_FIELD_LENGTH, max = MAX_FIELD_LENGTH, message = "{title} {errors.string.range}")
  @NotBlank(message = "{title} {errors.required.field}")
  private String title;

  @Size(max = MAX_FIELD_LENGTH, message = "{subject} {errors.string.max}")
  @NotBlank(message = "{subject} {errors.required.field}")
  private String subject;

  @Size(max = MAX_FIELD_LENGTH, message = "{license} {errors.string.max}")
  @NotBlank(message = "{licenseURL} {errors.required.field}")
  private String licenseUrl;

  // License names can be abbreviations, e.g. CC
  @Size(min = 2, max = MAX_FIELD_LENGTH, message = "{license} {errors.string.max}")
  @NotBlank(message = "{licenseName} {errors.required.field}")
  private String licenseName;

  @Size(max = MAX_FIELD_LENGTH, message = "{description} {errors.string.max}")
  @NotBlank(message = "{description} {errors.required.field}")
  private String description;

  private List<RepoDepositTag> tags;

  // can be empty
  private Map<String, String> otherProperties = new HashMap<>();

  private boolean publish;
}
