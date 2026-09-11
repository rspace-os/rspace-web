package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to perform a configured Inventory operation (DevDocs/adr/0007).
 *
 * <p>The client sends the values the user typed ({@code inputs}, keyed by the definition's input
 * keys), the origin subsamples with the amount taken from each, and the two wizard-level choices
 * (template, documentation target). The server validates the inputs against the definition {@code
 * operationType} names, builds the sample and every generated field itself ({@code
 * InventoryOperationRequestBuilder}) and applies the whole effect in one transaction, reducing each
 * origin by its amount taken and never increasing it. The built sample travels on {@link
 * #newSample}, which is not on the wire.
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"operationType", "origins", "inputs", "templateId", "documentedByGlobalId"})
public class ApiInventoryOperationPost {

  @JsonProperty("operationType")
  private String operationType;

  // Capped here as well as in InventoryOperationPostValidator (MAX_ORIGINS): the validator's check
  // runs only after Jackson has materialised every element and the @Valid cascade has walked all of
  // them, so the ceiling belongs at binding too.
  @Valid
  @Size(max = 100, message = "{errors.inventory.operation.tooManyOrigins}")
  @JsonProperty("origins")
  private List<ApiInventoryOperationOriginUpdate> origins = new ArrayList<>();

  /**
   * The values the user typed, by the definition's input key. Bound as raw JSON values (a quantity
   * arrives as a Map), which the endpoint types against the definition before validation. Absent
   * means no inputs, which is what Destroy declares.
   */
  @JsonProperty("inputs")
  private Map<String, Object> inputs;

  /**
   * The template for the sample the server builds; null means ad-hoc. Numeric like {@code POST
   * /samples} (M0, D4).
   */
  @JsonProperty("templateId")
  private Long templateId;

  /**
   * The ELN document, notebook or Gallery file the operation is documented by, as an {@code
   * IsDocumentedBy} link the server adds to the built sample. Named after the relation it creates.
   */
  @JsonProperty("documentedByGlobalId")
  private String documentedByGlobalId;

  /**
   * Server-side only: the sample the request builder assembled from the definition, the origins and
   * the inputs, which the manager's transactional core creates; null for a terminal operation
   * (Destroy). Not on the wire: the API mapper ignores a request property of this name exactly as
   * it ignores any property no DTO declares.
   */
  @JsonIgnore private ApiSampleWithFullSubSamples newSample;
}
