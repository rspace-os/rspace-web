/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.BasketsApiController.ApiBasketPost;
import com.researchspace.api.v1.model.ApiBasket;
import com.researchspace.api.v1.model.ApiBasketInfo;
import com.researchspace.api.v1.model.ApiGlobalIdsRequest;
import com.researchspace.model.User;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("/api/inventory/v1/baskets")
public interface BasketsApi {

  @GetMapping
  List<ApiBasketInfo> getBasketsForUser(User user);

  /** */
  @GetMapping(path = "/{basketId}")
  ApiBasket getBasketById(Long basketId, User user);

  /** */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiBasket createNewBasket(
      @RequestBody @Valid ApiBasketPost basketToCreate, BindingResult errors, User user)
      throws BindException;

  /** */
  @PutMapping(value = "/{basketId}")
  ApiBasket updateBasketDetails(
      Long basketId, @RequestBody @Valid ApiBasket basketUpdate, BindingResult errors, User user)
      throws BindException;

  /** */
  @PostMapping(path = "/{basketId}/addItems")
  ApiBasket addItemsToBasket(
      Long basketId,
      @RequestBody @Valid ApiGlobalIdsRequest basketItems,
      BindingResult errors,
      User user)
      throws BindException;

  /** */
  @PostMapping(path = "/{basketId}/removeItems")
  ApiBasket removeItemsFromBasket(
      Long basketId,
      @RequestBody @Valid ApiGlobalIdsRequest basketItems,
      BindingResult errors,
      User user)
      throws BindException;

  /** */
  @DeleteMapping(value = "/{basketId}")
  void deleteBasket(Long basketId, User user);
}
