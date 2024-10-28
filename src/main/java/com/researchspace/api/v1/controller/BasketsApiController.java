package com.researchspace.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.api.v1.BasketsApi;
import com.researchspace.api.v1.model.ApiBasket;
import com.researchspace.api.v1.model.ApiBasketInfo;
import com.researchspace.api.v1.model.ApiGlobalIdsRequest;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.inventory.BasketApiManager;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

@ApiController
public class BasketsApiController extends BaseApiInventoryController implements BasketsApi {

  @Autowired private BasketApiManager basketMgr;

  @Data
  @EqualsAndHashCode(callSuper = true)
  @NoArgsConstructor
  public static class ApiBasketPost extends ApiGlobalIdsRequest {

    @Size(
        max = BaseRecord.DEFAULT_VARCHAR_LENGTH,
        message = "Name cannot be longer than 255 characters")
    @JsonProperty("name")
    private String name;
  }

  @Override
  public List<ApiBasketInfo> getBasketsForUser(@RequestAttribute(name = "user") User user) {
    return basketMgr.getBasketsForUser(user);
  }

  @Override
  public ApiBasket getBasketById(
      @PathVariable Long basketId, @RequestAttribute(name = "user") User user) {
    return basketMgr.getBasketById(basketId, user);
  }

  @Override
  public ApiBasket createNewBasket(
      @RequestBody @Valid ApiBasketPost basketToCreate,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    if (basketToCreate == null) {
      basketToCreate = new ApiBasketPost();
    }
    return basketMgr.createNewBasket(basketToCreate.getName(), basketToCreate.getGlobalIds(), user);
  }

  @Override
  public ApiBasket updateBasketDetails(
      @PathVariable Long basketId,
      @RequestBody @Valid ApiBasket basketUpdate,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    basketUpdate.setId(basketId);
    return basketMgr.updateApiBasket(basketUpdate, user);
  }

  @Override
  public ApiBasket addItemsToBasket(
      @PathVariable Long basketId,
      @RequestBody @Valid ApiGlobalIdsRequest itemsGlobalIdsPost,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    ApiBasket apiBasket =
        basketMgr.addItemsToBasket(basketId, itemsGlobalIdsPost.getGlobalIds(), user);
    return apiBasket;
  }

  @Override
  public ApiBasket removeItemsFromBasket(
      @PathVariable Long basketId,
      @RequestBody @Valid ApiGlobalIdsRequest itemsGlobalIdsPost,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    ApiBasket apiBasket =
        basketMgr.removeItemsFromBasket(basketId, itemsGlobalIdsPost.getGlobalIds(), user);
    return apiBasket;
  }

  @Override
  public void deleteBasket(
      @PathVariable Long basketId, @RequestAttribute(name = "user") User user) {

    basketMgr.deleteBasketById(basketId, user);
  }
}
