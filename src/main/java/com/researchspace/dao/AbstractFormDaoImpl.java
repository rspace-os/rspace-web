package com.researchspace.dao;

import com.researchspace.model.record.AbstractForm;
import com.researchspace.model.record.FormType;
import java.util.Optional;

public class AbstractFormDaoImpl<T extends AbstractForm> extends GenericDaoHibernate<T, Long>
    implements AbstractFormDao<T, Long> {

  public AbstractFormDaoImpl(Class<T> persistentClass) {
    super(persistentClass);
  }

  @Override
  public Optional<FormType> getTypeById(Long id) {
    return Optional.ofNullable(
        getSession()
            .createQuery("select formType from AbstractForm where id=:id", FormType.class)
            .setParameter("id", id)
            .uniqueResult());
  }
}
