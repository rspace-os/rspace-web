package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.EditInfo;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

public final class ApiInventoryOperationRequests {

  private ApiInventoryOperationRequests() {}

  /**
   * One origin subsample. {@code amountTaken} is what the operation removes from it, and is sent
   * exactly when the operation takes a chosen amount.
   */
  @Getter
  @Setter
  public static class Origin {
    @JsonProperty("globalId")
    private String globalId;

    @JsonProperty("amountTaken")
    private ApiQuantityInfo amountTaken;
  }

  public interface Request {
    /** The origins in request order. */
    List<Origin> originList();

    default Long getTemplateId() {
      return null;
    }

    default String getDocumentedByGlobalId() {
      return null;
    }
  }

  @Getter
  @Setter
  public abstract static class Creating implements Request {
    @NotBlank(message = "{errors.inventory.operation.inputRequired}")
    @Size(
        max = BaseRecord.DEFAULT_VARCHAR_LENGTH,
        message = "{errors.inventory.operation.inputTooLong}")
    @JsonProperty("sampleName")
    private String sampleName;

    /**
     * How many subsamples to create, a whole number from 1 to 100; absent means one. A BigDecimal,
     * not an Integer, so Jackson cannot truncate {@code 1.9} to 1, and it keeps the JSON text's
     * scale, so {@code @Digits} rejects {@code 1.0} as well.
     */
    @Min(value = 1, message = "{errors.inventory.operation.inputBelowMinimum}")
    @Max(value = 100, message = "{errors.inventory.operation.inputAboveMaximum}")
    @Digits(
        integer = Integer.MAX_VALUE,
        fraction = 0,
        message = "{errors.inventory.operation.countNotWhole}")
    @JsonProperty("count")
    private BigDecimal count;

    @NotNull(message = "{errors.inventory.operation.inputRequired}")
    @JsonProperty("eachAmount")
    private ApiQuantityInfo eachAmount;

    @JsonProperty("templateId")
    private Long templateId;

    @JsonProperty("documentedByGlobalId")
    private String documentedByGlobalId;
  }

  @Getter
  @Setter
  public abstract static class SingleOriginCreating extends Creating {
    @NotNull(message = "{errors.inventory.operation.originsRequired}")
    @JsonProperty("origin")
    private Origin origin;

    @Override
    public List<Origin> originList() {
      return List.of(origin);
    }
  }

  public static class Aliquot extends SingleOriginCreating {}

  public static class Passage extends SingleOriginCreating {}

  @Getter
  @Setter
  public static class Derive extends SingleOriginCreating {
    @NotBlank(message = "{errors.inventory.operation.inputRequired}")
    @Size(
        max = BaseRecord.DEFAULT_VARCHAR_LENGTH,
        message = "{errors.inventory.operation.inputTooLong}")
    @JsonProperty("processName")
    private String processName;
  }

  @Getter
  @Setter
  public static class Cryopreserve extends SingleOriginCreating {
    /** Stored as the created sample's cryomedium field content, hence the description limit. */
    @Size(max = EditInfo.DESCRIPTION_LENGTH, message = "{errors.inventory.operation.inputTooLong}")
    @JsonProperty("cryomedium")
    private String cryomedium;

    @NotNull(message = "{errors.inventory.operation.inputRequired}")
    @JsonProperty("storageTemp")
    private ApiQuantityInfo storageTemp;
  }

  /** {@code storageTemp} defaults to 4 degrees Celsius. */
  @Getter
  @Setter
  public static class Revive extends SingleOriginCreating {
    @JsonProperty("storageTemp")
    private ApiQuantityInfo storageTemp;
  }

  @Getter
  @Setter
  public static class Pool extends Creating {
    @NotNull(message = "{errors.inventory.operation.originsRequired}")
    @Size.List({
      @Size(min = 2, message = "{errors.inventory.operation.originCountMinimum}"),
      @Size(max = 100, message = "{errors.inventory.operation.tooManyOrigins}")
    })
    @JsonProperty("origins")
    private List<Origin> origins;

    @JsonProperty("takeAll")
    private Boolean takeAll;

    @Override
    public List<Origin> originList() {
      return origins == null ? List.of() : origins;
    }

    public boolean takesAll() {
      return Boolean.TRUE.equals(takeAll);
    }
  }

  @Getter
  @Setter
  public static class Destroy implements Request {
    @NotNull(message = "{errors.inventory.operation.originsRequired}")
    @JsonProperty("origin")
    private Origin origin;

    @Override
    public List<Origin> originList() {
      return List.of(origin);
    }
  }
}
