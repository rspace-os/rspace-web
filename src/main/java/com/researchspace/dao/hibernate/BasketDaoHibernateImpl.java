package com.researchspace.dao.hibernate;

import com.researchspace.dao.BasketDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Basket;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class BasketDaoHibernateImpl extends GenericDaoHibernate<Basket, Long> implements BasketDao {

  public BasketDaoHibernateImpl(Class<Basket> persistentClass) {
    super(persistentClass);
  }

  public BasketDaoHibernateImpl() {
    super(Basket.class);
  }

  @Override
  public List<Basket> getBasketsForUser(User user) {
    return sessionFactory
        .getCurrentSession()
        .createQuery("from Basket where owner=:owner ", Basket.class)
        .setParameter("owner", user)
        .list();
  }
}
