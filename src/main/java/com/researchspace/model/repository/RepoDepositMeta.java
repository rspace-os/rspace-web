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
 * <p>Validation messages are resolved through the application message source.
 */
@Data
@NoArgsConstructor
public class RepoDepositMeta {
  protected static final int MAX_USERS = 10;
  protected static final int MAX_FIELD_LENGTH = 1000;
  protected static final int MIN_FIELD_LENGTH = 3;

  @Size(
      max = MAX_USERS,
      message = "{validation.errors.collectionRange} {validation.fields.authors}")
  @NotEmpty(message = "{validation.fields.authors} {validation.errors.requiredField}")
  private @Valid List<UserDepositorAdapter> authors;

  @Size(max = 10, message = "{validation.errors.collectionRange} {validation.fields.contacts}")
  @NotEmpty(message = "{validation.fields.contacts} {validation.errors.requiredField}")
  private @Valid List<UserDepositorAdapter> contacts;

  @Size(
      min = MIN_FIELD_LENGTH,
      max = MAX_FIELD_LENGTH,
      message = "{workspace:export.repositories.common.title} {validation.errors.stringRange}")
  @NotBlank(
      message = "{workspace:export.repositories.common.title} {validation.errors.requiredField}")
  private String title;

  @Size(
      max = MAX_FIELD_LENGTH,
      message = "{workspace:export.repositories.dataverse.subject} {validation.errors.stringMax}")
  @NotBlank(
      message =
          "{workspace:export.repositories.dataverse.subject} {validation.errors.requiredField}")
  private String subject;

  @Size(
      max = MAX_FIELD_LENGTH,
      message = "{workspace:export.repositories.dataverse.license} {validation.errors.stringMax}")
  @NotBlank(message = "{validation.fields.licenseUrl} {validation.errors.requiredField}")
  private String licenseUrl;

  // License names can be abbreviations, e.g. CC
  @Size(
      min = 2,
      max = MAX_FIELD_LENGTH,
      message = "{workspace:export.repositories.dataverse.license} {validation.errors.stringMax}")
  @NotBlank(message = "{validation.fields.licenseName} {validation.errors.requiredField}")
  private String licenseName;

  @Size(
      max = MAX_FIELD_LENGTH,
      message = "{workspace:export.repositories.common.description} {validation.errors.stringMax}")
  @NotBlank(
      message =
          "{workspace:export.repositories.common.description} {validation.errors.requiredField}")
  private String description;

  private List<RepoDepositTag> tags;

  // can be empty
  private Map<String, String> otherProperties = new HashMap<>();

  private boolean publish;
}
