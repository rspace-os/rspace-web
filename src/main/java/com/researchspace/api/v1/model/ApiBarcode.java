/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.inventory.Barcode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * An image, attachment or linked resource object, but without its binary data.
 *
 * <p>It is an API representation of an EcatMediaFile entity.
 *
 * <p>
 *
 * @implementation ApiFile is cached. It is almost immutable apart from the <em>name</em> property.
 *     Renaming triggers eviction from the Spring cache using an event/listener mechanism. <br>
 *     If new mutable properties are added in future, a similar mechanism will need to be used to
 *     indicate that the cached ApiFile should be evicted.
 */
@Data
@EqualsAndHashCode
@NoArgsConstructor
@JsonPropertyOrder(
    value = {"id", "data", "format", "description", "created", "createdBy", "_links"})
public class ApiBarcode extends LinkableApiObject {

  @JsonProperty("id")
  private Long id;

  @JsonProperty("data")
  private String data;

  @JsonProperty("format")
  private String format;

  @JsonProperty("description")
  private String description;

  @EqualsAndHashCode.Exclude
  @JsonProperty("created")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long createdMillis;

  @JsonProperty("createdBy")
  private String createdBy;

  @JsonProperty(value = "newBarcodeRequest", access = JsonProperty.Access.WRITE_ONLY)
  private boolean newBarcodeRequest;

  @JsonProperty(value = "deleteBarcodeRequest", access = JsonProperty.Access.WRITE_ONLY)
  private boolean deleteBarcodeRequest;

  public ApiBarcode(Barcode barcode) {
    setId(barcode.getId());
    setData(barcode.getBarcodeData());
    setFormat(barcode.getFormat());
    setDescription(barcode.getDescription());
    setCreatedMillis(barcode.getCreationDate().getTime());
    setCreatedBy(barcode.getCreatedBy());
  }

  public ApiBarcode(String data) {
    setData(data);
  }

  public boolean applyChangesToDatabaseBarcode(Barcode dbBarcode) {
    boolean contentChanged = false;

    if (getData() != null) {
      if (!getData().equals(dbBarcode.getBarcodeData())) {
        dbBarcode.setBarcodeData(getData());
        contentChanged = true;
      }
    }
    if (getDescription() != null) {
      if (!getDescription().equals(dbBarcode.getDescription())) {
        dbBarcode.setDescription(getDescription());
        contentChanged = true;
      }
    }
    if (getFormat() != null) {
      if (!getFormat().equals(dbBarcode.getFormat())) {
        dbBarcode.setFormat(getFormat());
        contentChanged = true;
      }
    }
    return contentChanged;
  }
}
