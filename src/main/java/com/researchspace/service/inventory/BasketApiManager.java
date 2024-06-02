package com.researchspace.service.inventory;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.researchspace.api.v1.model.ApiBasket;
import com.researchspace.api.v1.model.ApiBasketInfo;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import java.util.List;

/** To deal with inventory export requests. */
public interface BasketApiManager {

  List<ApiBasketInfo> getBasketsForUser(User user);

  ApiBasket getBasketById(Long basketId, User user);

  ApiBasket createNewBasket(String name, List<String> globalIds, User user);

  ApiBasket updateApiBasket(ApiBasket basketUpdate, User user);

  ApiBasket addItemsToBasket(Long basketId, List<String> itemGlobalIds, User user);

  ApiBasket removeItemsFromBasket(Long basketId, List<String> itemGlobalIds, User user);

  void deleteBasketById(Long basketId, User user);

  ApiInventorySearchResult searchForBasketContent(
      Long basketId,
      String ownedBy,
      InventorySearchType searchType,
      InventorySearchDeletedOption deletedItemsOption,
      PaginationCriteria<InventoryRecord> pgCrit,
      User user);
}
