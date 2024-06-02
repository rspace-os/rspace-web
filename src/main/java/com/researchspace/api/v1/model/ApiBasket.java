/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.inventory.Basket;
import com.researchspace.model.inventory.InventoryRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** A basket storing a selection of inventory items. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(value = {"id", "globalId", "name", "itemCount", "items"})
public class ApiBasket extends ApiBasketInfo {

  @JsonProperty("items")
  private List<ApiInventoryRecordInfo> items = new ArrayList<>();

  public ApiBasket(
      Basket basket, Function<InventoryRecord, ApiInventoryRecordInfo> invRecInContentMapper) {
    super(basket);

    List<ApiInventoryRecordInfo> basketItems =
        basket.getItems().stream()
            .map(basketItem -> basketItem.getInventoryRecord())
            .map(invRecInContentMapper)
            .collect(Collectors.toList());
    setItems(basketItems);
  }

  @Transient
  public List<String> getContentGlobalIds() {
    if (items == null) {
      return null;
    }
    return items.stream().map(ApiInventoryRecordInfo::getGlobalId).collect(Collectors.toList());
  }
}
