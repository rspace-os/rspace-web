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
     * How many subsamples to create; absent means one.
     *
     * <p>Bound as a BigDecimal, NOT an Integer, so a fractional count survives binding long enough
     * to be rejected by {@code @Digits}. Jackson's {@code ACCEPT_FLOAT_AS_INT} is on by default and
     * the API converter does not turn it off, so an Integer field would take {@code "count": 1.9}
     * as 1: the request would deduct the full amountTaken but create one subsample, and {@code
     * 100.9} would become 100 and slip past the maximum.
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
    // Two constraints rather than one @Size(min, max) so each bound keeps its own message: a single
    // annotation carries a single message, which would report a 101-origin request as "requires at
    // least two".
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
