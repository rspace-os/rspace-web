/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.inventory.Basket;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** A basket storing a selection of inventory items. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder(value = {"id", "globalId", "name", "itemCount"})
public class ApiBasketInfo extends IdentifiableNameableApiObject {

  @JsonProperty("itemCount")
  private long itemCount;

  public ApiBasketInfo(Basket basket) {
    setId(basket.getId());
    setGlobalId(basket.getOid().getIdString());
    setName(basket.getName());
    setItemCount(basket.getItemCount());
  }
}
