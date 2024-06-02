package com.researchspace.model.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExportSelection {

  public enum ExportType {
    @JsonProperty("selection")
    SELECTION,
    @JsonProperty("user")
    USER,
    @JsonProperty("group")
    GROUP
  }

  @NotNull(message = "type {errors.required.field}")
  private ExportType type;

  // In case type = SELECTION, these must be filled.
  private Long[] exportIds;
  private String[] exportNames;
  private String[] exportTypes;

  // In case type = USER, username (of the user whose work is being exported) must be provided.
  private String username;

  // In case type = GROUP, group id (of the group whose work is being exported) must be provided.
  private Long groupId;

  public static ExportSelection createRecordsExportSelection(
      Long[] exportIds, String[] exportTypes) {
    ExportSelection userExportSelection = new ExportSelection();
    userExportSelection.setType(ExportType.SELECTION);
    userExportSelection.setExportIds(exportIds);
    userExportSelection.setExportTypes(exportTypes);
    return userExportSelection;
  }

  public static ExportSelection createUserExportSelection(String username) {
    ExportSelection userExportSelection = new ExportSelection();
    userExportSelection.setType(ExportType.USER);
    userExportSelection.setUsername(username);
    return userExportSelection;
  }

  public static ExportSelection createGroupExportSelection(Long groupId) {
    ExportSelection groupExportSelection = new ExportSelection();
    groupExportSelection.setType(ExportType.GROUP);
    groupExportSelection.setGroupId(groupId);
    return groupExportSelection;
  }
}
