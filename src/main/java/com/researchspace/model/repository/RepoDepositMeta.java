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

  @Size(max = MAX_USERS, message = "{validation.errors.authorsCollectionRange}")
  @NotEmpty(message = "{validation.errors.authorsRequired}")
  private @Valid List<UserDepositorAdapter> authors;

  @Size(max = 10, message = "{validation.errors.contactsCollectionRange}")
  @NotEmpty(message = "{validation.errors.contactsRequired}")
  private @Valid List<UserDepositorAdapter> contacts;

  @Size(
      min = MIN_FIELD_LENGTH,
      max = MAX_FIELD_LENGTH,
      message = "{validation.errors.titleStringRange}")
  @NotBlank(message = "{validation.errors.titleRequired}")
  private String title;

  @Size(max = MAX_FIELD_LENGTH, message = "{validation.errors.subjectStringMax}")
  @NotBlank(message = "{validation.errors.subjectRequired}")
  private String subject;

  @Size(max = MAX_FIELD_LENGTH, message = "{validation.errors.licenseUrlStringMax}")
  @NotBlank(message = "{validation.errors.licenseUrlRequired}")
  private String licenseUrl;

  // License names can be abbreviations, e.g. CC
  @Size(min = 2, max = MAX_FIELD_LENGTH, message = "{validation.errors.licenseNameStringRange}")
  @NotBlank(message = "{validation.errors.licenseNameRequired}")
  private String licenseName;

  @Size(max = MAX_FIELD_LENGTH, message = "{validation.errors.descriptionStringMax}")
  @NotBlank(message = "{validation.errors.descriptionRequired}")
  private String description;

  private List<RepoDepositTag> tags;

  // can be empty
  private Map<String, String> otherProperties = new HashMap<>();

  private boolean publish;
}
