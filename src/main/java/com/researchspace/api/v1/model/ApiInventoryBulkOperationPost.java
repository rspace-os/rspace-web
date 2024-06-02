package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
public class ApiInventoryBulkOperationPost {

  @NotNull(message = "Bulk operation must specify operationType")
  @JsonProperty("operationType")
  private BulkApiOperationType operationType;

  @JsonProperty("rollbackOnError")
  private boolean rollbackOnError = true;

  @NotNull(message = "Bulk operation must specify list of records")
  @Size(min = 1, max = 100, message = "Bulk operation must specify at least 1, at most 100 records")
  @JsonProperty("records")
  private List<ApiInventoryRecordInfo> records = new ArrayList<>();

  public enum BulkApiOperationType {
    /* publicly supported operations, described in swagger */
    CREATE,
    UPDATE,
    DELETE,
    RESTORE,
    DUPLICATE,
    MOVE,
    CHANGE_OWNER,

    /* undocumented in swagger, for calling internally */
    UPDATE_TO_LATEST_TEMPLATE_VERSION;
  }
}
