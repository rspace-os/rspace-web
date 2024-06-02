package com.researchspace.service.inventory.impl;

import static org.apache.commons.lang.StringUtils.isNotBlank;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.researchspace.api.v1.model.ApiBasket;
import com.researchspace.api.v1.model.ApiBasketInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.dao.BasketDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Basket;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.BasketApiManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.SampleApiManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("basketApiManager")
public class BasketApiManagerImpl implements BasketApiManager {

  private @Autowired BasketDao basketDao;
  private @Autowired InventoryPermissionUtils invPermissions;
  private @Autowired MessageSourceUtils messages;
  private @Autowired SampleApiManager sampleApiManager;

  @Override
  public List<ApiBasketInfo> getBasketsForUser(User user) {
    List<Basket> userBaskets = basketDao.getBasketsForUser(user);
    return userBaskets.stream().map(b -> new ApiBasketInfo(b)).collect(Collectors.toList());
  }

  @Override
  public ApiBasket getBasketById(Long basketId, User user) {
    Basket basket = getIfExists(basketId);
    return getApiBasketWithPopulatedContent(basket, user);
  }

  public Basket getIfExists(Long basketId) {
    Optional<Basket> dbLomOpt = basketDao.getSafeNull(basketId);
    if (!dbLomOpt.isPresent()) {
      throwBasketNotFoundException(basketId);
    }
    return dbLomOpt.get();
  }

  private void throwBasketNotFoundException(Long lomId) {
    String msg = messages.getResourceNotFoundMessage("Basket", lomId);
    throw new NotFoundException(msg);
  }

  @Override
  public ApiBasket createNewBasket(String name, List<String> globalIds, User user) {
    Basket newBasket = new Basket();

    String newBasketName = name;
    if (StringUtils.isBlank(name)) {
      int userBasketCount = getBasketsForUser(user).size();
      newBasketName = "Basket #" + ++userBasketCount;
    }
    newBasket.setName(newBasketName);
    newBasket.setOwner(user);
    if (globalIds != null) {
      addItemsToBasket(newBasket, globalIds, user);
    }

    Basket savedBasket = basketDao.save(newBasket);
    return getApiBasketWithPopulatedContent(savedBasket, user);
  }

  @Override
  public ApiBasket updateApiBasket(ApiBasket basketUpdate, User user) {
    Basket basket = getIfExists(basketUpdate.getId());
    if (StringUtils.isNotBlank(basketUpdate.getName())) {
      basket.setName(basketUpdate.getName());
    }
    return getApiBasketWithPopulatedContent(basket, user);
  }

  @Override
  public ApiBasket addItemsToBasket(Long basketId, List<String> itemGlobalIds, User user) {
    Basket basket = getIfExists(basketId);
    addItemsToBasket(basket, itemGlobalIds, user);
    return getApiBasketWithPopulatedContent(basket, user);
  }

  private void addItemsToBasket(Basket basket, List<String> itemGlobalIds, User user) {
    if (itemGlobalIds != null) {
      for (String globalIdString : itemGlobalIds) {
        GlobalIdentifier globalId = new GlobalIdentifier(globalIdString);
        InventoryRecord invRec =
            invPermissions.getInvRecByGlobalIdOrThrowNotFoundException(globalId);
        basket.addInventoryItem(invRec);
      }
    }
  }

  @Override
  public ApiBasket removeItemsFromBasket(Long basketId, List<String> itemGlobalIds, User user) {
    Basket basket = getIfExists(basketId);
    if (itemGlobalIds != null) {
      for (String globalIdString : itemGlobalIds) {
        GlobalIdentifier globalId = new GlobalIdentifier(globalIdString);
        InventoryRecord invRec =
            invPermissions.getInvRecByGlobalIdOrThrowNotFoundException(globalId);
        basket.removeInventoryItem(invRec);
      }
    }
    return getApiBasketWithPopulatedContent(basket, user);
  }

  @NotNull
  private ApiBasket getApiBasketWithPopulatedContent(Basket basket, User user) {
    Function<InventoryRecord, ApiInventoryRecordInfo> outgoingContentMapper =
        (invRec) -> {
          ApiInventoryRecordInfo resultApiInvRec =
              ApiInventoryRecordInfo.fromInventoryRecord(invRec);
          sampleApiManager.setOtherFieldsForOutgoingApiInventoryRecord(
              resultApiInvRec, invRec, user);
          return resultApiInvRec;
        };
    return new ApiBasket(basket, outgoingContentMapper);
  }

  @Override
  public void deleteBasketById(Long basketId, User user) {
    basketDao.remove(basketId);
  }

  @Override
  public ApiInventorySearchResult searchForBasketContent(
      Long basketId,
      String ownedBy,
      InventorySearchType searchType,
      InventorySearchDeletedOption deletedItemsOption,
      PaginationCriteria<InventoryRecord> pgCrit,
      User user) {

    ApiBasket apiBasket = getBasketById(basketId, user);
    if (apiBasket == null) {
      throw new NotFoundException("Basket with id: " + basketId + " does not exist");
    }
    List<InventoryRecord> children = new ArrayList<>();
    for (String globalIdString : apiBasket.getContentGlobalIds()) {
      children.add(
          invPermissions.getInvRecByGlobalIdOrThrowNotFoundException(
              new GlobalIdentifier(globalIdString)));
    }
    if (searchType != null && searchType != InventorySearchType.ALL) {
      children =
          children.stream()
              .filter(
                  ir ->
                      ir.getType()
                          .equals(
                              InventoryRecord.InventoryRecordType.valueOf(searchType.toString())))
              .collect(Collectors.toList());
    }
    if (isNotBlank(ownedBy)) {
      children =
          children.stream()
              .filter(ir -> ir.getOwner().getUsername().equals(ownedBy))
              .collect(Collectors.toList());
    }
    children =
        children.stream()
            .filter(rec -> isMatchingDeletedItemsOption(rec, deletedItemsOption))
            .collect(Collectors.toList());

    return sampleApiManager.sortRepaginateConvertToApiInventorySearchResult(pgCrit, children, user);
  }

  private boolean isMatchingDeletedItemsOption(
      InventoryRecord record, InventorySearchDeletedOption deletedOption) {
    switch (deletedOption) {
      case DELETED_ONLY:
        return record.isDeleted();
      case EXCLUDE:
        return !record.isDeleted();
      case INCLUDE:
      default:
        return true;
    }
  }
}
