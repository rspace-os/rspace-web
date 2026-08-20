package com.researchspace.b2inst.model.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PIDINST core metadata profile for a B2INST instrument record.
 *
 * <p>This object is the {@code metadata} block sent when creating a draft record
 * and echoed back, with a server-populated {@link #communityExtension},
 * inside the draft record. The JSON keys follow the PIDINST
 * PascalCase convention (except the server-populated {@code community_extension},
 * which is snake_case) and are mapped to idiomatic Java field names via
 * {@link JsonProperty}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instInstrumentMetadata {

  /** Human-readable name of the instrument. */
  @JsonProperty("Name")
  private String name;

  /** Persistent identifier (for example a Handle) that resolves to the instrument. */
  @JsonProperty("Identifier")
  private B2instIdentifier identifier;

  /** Version of the PIDINST schema the record conforms to (for example {@code "1.0"}). */
  @JsonProperty("SchemaVersion")
  private String schemaVersion;

  /** Free-text description of the instrument and its purpose. */
  @JsonProperty("Description")
  private String description;

  /** One or more controlled-vocabulary instrument types. */
  @JsonProperty("InstrumentType")
  private List<B2instInstrumentType> instrumentType;

  /** Organisation(s) that manufactured the instrument. */
  @JsonProperty("Manufacturer")
  private List<B2instManufacturer> manufacturer;

  /** Organisation(s) that own the instrument. */
  @JsonProperty("Owner")
  private List<B2instOwner> owner;

  /** Manufacturer model of the instrument. */
  @JsonProperty("Model")
  private B2instModel model;

  /** Physical or environmental variables the instrument measures. */
  @JsonProperty("MeasuredVariable")
  private List<String> measuredVariable;

  /**
   * Commissioning/decommissioning dates of the instrument. Note that each entry mixes conventions:
   * the inner date key is PascalCase {@code Date} but its type key is lowerCamelCase {@code
   * dateType}, per the B2INST wire format. See {@link B2instDate}.
   */
  @JsonProperty("Date")
  private List<B2instDate> date;

  /** URL of the instrument's landing page. */
  @JsonProperty("LandingPage")
  private String landingPage;

  /** Local or alternate identifiers (for example inventory numbers). */
  @JsonProperty("AlternateIdentifier")
  private List<B2instAlternateIdentifier> alternateIdentifier;

  /** Identifiers of related resources (manuals, datasets, papers and so on). */
  @JsonProperty("RelatedIdentifier")
  private List<B2instRelatedIdentifier> relatedIdentifier;

  /**
   * Community-specific schema extension. Omitted (or empty) on creation and
   * populated by the server with any community extension fields when read back.
   */
  @JsonProperty("community_extension")
  private Map<String, Object> communityExtension;
}
