package com.researchspace.service.impl;

import com.researchspace.dao.GenericDao;
import com.researchspace.service.GenericManager;
import java.io.Serializable;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class serves as the Base class for all other Managers - namely to hold common CRUD methods
 * that they might all use. You should only need to extend this class when your require custom CRUD
 * logic.
 *
 * <p>
 */
public class GenericManagerImpl<T, PK extends Serializable> implements GenericManager<T, PK> {
  /** Logger variable for all child classes. Uses slf4j */
  protected final Logger log = LoggerFactory.getLogger(getClass());

  /** GenericDao instance, set by constructor of child classes */
  protected GenericDao<T, PK> dao;

  public GenericManagerImpl() {}

  public GenericManagerImpl(GenericDao<T, PK> genericDao) {
    this.dao = genericDao;
  }

  /** {@inheritDoc} */
  public List<T> getAll() {
    return dao.getAll();
  }

  /** {@inheritDoc} */
  public T get(PK id) {
    return dao.get(id);
  }

  /** {@inheritDoc} */
  public boolean exists(PK id) {
    return dao.exists(id);
  }

  /** {@inheritDoc} */
  public T save(T object) {
    return dao.save(object);
  }

  /** {@inheritDoc} */
  public void remove(PK id) {
    dao.remove(id);
  }

  public void setDao(GenericDao<T, PK> dao) {
    this.dao = dao;
  }
}
