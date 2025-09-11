package com.researchspace.api.v1.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentShares {
  private long sharedDocId;
  private String sharedDocName;

  private List<Share> notebookShares;
  private List<Share> directShares;

  @Data
  @Builder
  public static class Share {
    private long shareId;
    private Long sharerId;
    private String sharerName;
    private String recipientName;
    private RecipientType recipientType;
    private PermissionType permission;
    private long locationId;
    private String locationName;
  }

  public enum PermissionType {
    READ,
    EDIT
  }

  public enum RecipientType {
    USER,
    GROUP
  }
}
