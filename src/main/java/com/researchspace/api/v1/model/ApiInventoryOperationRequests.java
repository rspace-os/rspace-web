package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * The request bodies of the seven typed operation endpoints, {@code POST /operations/<key>}: the
 * public API contract (DevDocs/adr/0007 M6, shapes frozen in DevDocs/adr/0007). Each carries what
 * is consumed ({@code origin}, or {@code origins} for Pool, identified by global id, M0 D3), the
 * values the definition declares as inputs, and for a creating operation the template (numeric like
 * {@code POST /samples}, D4) and the documentation target (D8: no other sample metadata; set it
 * with a follow-up {@code PUT}).
 *
 * <p>Every input field is named exactly after the definition's input key, so {@link
 * Request#toOperationInputs()} is a mechanical copy and a core validation error about, say, {@code
 * sampleName} already names the field the client sent. The classes validate SHAPE only: presence of
 * the origin, the list size a generated client can enforce. Every value rule (presence of a
 * required input, bounds, amount semantics, cardinality) is the config-driven core's, applied once,
 * so nothing here can drift from the definition; {@code InventoryOperationFacadeShapesTest} pins
 * the agreement.
 */
public final class ApiInventoryOperationRequests {

  private ApiInventoryOperationRequests() {}

  /**
   * One origin subsample. {@code amountTaken} is what the operation removes from it, and is only
   * meaningful where the definition takes something (absent for Passage and Destroy: the server
   * takes nothing, or everything). {@code expectedQuantity} is optional on every operation (M0 D5):
   * the quantity the caller saw, compare-and-swapped against the live locked quantity, a mismatch
   * being a 409 to reload from. Absent, the operation takes whatever is there.
   */
  @Getter
  @Setter
  public static class Origin {
    @JsonProperty("globalId")
    private String globalId;

    @JsonProperty("amountTaken")
    private ApiQuantityInfo amountTaken;

    @JsonProperty("expectedQuantity")
    private ApiQuantityInfo expectedQuantity;
  }

  /** What the endpoint reads off any of the seven, whatever their fields. */
  public interface Request {
    /** The origins in request order; a single-origin request wraps its one origin. */
    List<Origin> originList();

    /** The declared inputs the client sent, by input key; absent ones are left out. */
    Map<String, Object> toOperationInputs();

    default Long getTemplateId() {
      return null;
    }

    default String getDocumentedByGlobalId() {
      return null;
    }
  }

  /** The fields every creating operation declares. */
  @Getter
  @Setter
  public abstract static class Creating implements Request {
    @JsonProperty("sampleName")
    private String sampleName;

    @JsonProperty("count")
    private Integer count;

    @JsonProperty("eachAmount")
    private ApiQuantityInfo eachAmount;

    @JsonProperty("templateId")
    private Long templateId;

    @JsonProperty("documentedByGlobalId")
    private String documentedByGlobalId;

    @Override
    public Map<String, Object> toOperationInputs() {
      Map<String, Object> inputs = new LinkedHashMap<>();
      put(inputs, "sampleName", sampleName);
      put(inputs, "count", count);
      put(inputs, "eachAmount", eachAmount);
      return inputs;
    }

    static void put(Map<String, Object> inputs, String key, Object value) {
      if (value != null) {
        inputs.put(key, value);
      }
    }
  }

  /** A creating operation over exactly one origin (M0 D6: singular {@code origin}). */
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

  /** {@code POST /operations/aliquot}. */
  public static class Aliquot extends SingleOriginCreating {}

  /** {@code POST /operations/passage}. */
  public static class Passage extends SingleOriginCreating {}

  /** {@code POST /operations/derive}. */
  @Getter
  @Setter
  public static class Derive extends SingleOriginCreating {
    @JsonProperty("processName")
    private String processName;

    @Override
    public Map<String, Object> toOperationInputs() {
      Map<String, Object> inputs = super.toOperationInputs();
      put(inputs, "processName", processName);
      return inputs;
    }
  }

  /** {@code POST /operations/cryopreserve}. */
  @Getter
  @Setter
  public static class Cryopreserve extends SingleOriginCreating {
    @JsonProperty("cryomedium")
    private String cryomedium;

    @JsonProperty("storageTemp")
    private ApiQuantityInfo storageTemp;

    @Override
    public Map<String, Object> toOperationInputs() {
      Map<String, Object> inputs = super.toOperationInputs();
      put(inputs, "cryomedium", cryomedium);
      put(inputs, "storageTemp", storageTemp);
      return inputs;
    }
  }

  /** {@code POST /operations/revive}. */
  @Getter
  @Setter
  public static class Revive extends SingleOriginCreating {
    @JsonProperty("storageTemp")
    private ApiQuantityInfo storageTemp;

    @Override
    public Map<String, Object> toOperationInputs() {
      Map<String, Object> inputs = super.toOperationInputs();
      put(inputs, "storageTemp", storageTemp);
      return inputs;
    }
  }

  /**
   * {@code POST /operations/pool}: the one multi-origin operation (M0 D6: plural {@code origins}),
   * each origin with its own amount taken. {@code minItems} is the one list rule kept here because
   * a generated client can enforce it before the call; the core re-checks it against the
   * definition's {@code requiresMultiple}.
   */
  @Getter
  @Setter
  public static class Pool extends Creating {
    // Two constraints rather than one @Size(min, max) so each bound keeps its own message: a single
    // annotation carries a single message, which would report a 101-origin request as "requires at
    // least two". The ceiling is capped here as well as in InventoryOperationPostValidator
    // (MAX_ORIGINS), for the same reason the generic request caps it: the core's check runs only
    // after Jackson has materialised every element and performTyped has walked all of them parsing
    // global ids, so the ceiling belongs at binding too (parallel review). Same key and same value
    // as ApiInventoryOperationPost, so the two endpoints cannot disagree.
    @Size.List({
      @Size(min = 2, message = "{errors.inventory.operation.originCountMinimum}"),
      @Size(max = 100, message = "{errors.inventory.operation.tooManyOrigins}")
    })
    @JsonProperty("origins")
    private List<Origin> origins;

    @Override
    public List<Origin> originList() {
      return origins == null ? List.of() : origins;
    }
  }

  /** {@code POST /operations/destroy}: empties the origin and creates nothing (200, not 201). */
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

    @Override
    public Map<String, Object> toOperationInputs() {
      return Map.of();
    }
  }
}
