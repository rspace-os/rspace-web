package com.researchspace.dao.hibernate;

import com.researchspace.dao.FieldFormDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.field.FieldForm;
import org.springframework.stereotype.Repository;

@Repository("fieldFormDao")
public class FieldFormHibernateDao extends GenericDaoHibernate<FieldForm, Long>
    implements FieldFormDao {

  public FieldFormHibernateDao() {
    super(FieldForm.class);
  }
}
