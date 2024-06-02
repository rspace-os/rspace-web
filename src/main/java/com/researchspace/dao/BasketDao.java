package com.researchspace.dao;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Basket;
import java.util.List;

/** For DAO operations on Inventory Container. */
public interface BasketDao extends GenericDao<Basket, Long> {

  /** */
  List<Basket> getBasketsForUser(User user);
}
