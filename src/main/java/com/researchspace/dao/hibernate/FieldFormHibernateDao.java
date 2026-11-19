package com.researchspace.dao.hibernate;

import com.researchspace.dao.FieldFormDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.field.FieldForm;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository("fieldFormDao")
public class FieldFormHibernateDao extends GenericDaoHibernate<FieldForm, Long>
    implements FieldFormDao {

  public FieldFormHibernateDao() {
    super(FieldForm.class);
  }

  @Override
  public List<String> getFieldKeysForForm(Long formId) {
    return getSession()
        .createQuery(
            "select ff.fieldKey from FieldForm ff where ff.form.id = :formId"
                + " and ff.fieldKey is not null",
            String.class)
        .setParameter("formId", formId)
        .list();
  }
}
