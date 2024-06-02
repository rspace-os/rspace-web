package com.researchspace.dao.hibernate;

import com.researchspace.dao.DAOUtils;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.stereotype.Component;

@Component("daoUtils")
public class HibernateUtils implements DAOUtils {

  public <T> T initializeAndUnproxy(T entity) {
    if (entity == null) {
      throw new NullPointerException("Entity passed for initialization is null");
    }

    Hibernate.initialize(entity);
    if (entity instanceof HibernateProxy) {
      entity = (T) ((HibernateProxy) entity).getHibernateLazyInitializer().getImplementation();
    }
    return entity;
  }
}
