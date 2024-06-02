package com.researchspace.dao.hibernate;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.FormCreateMenuDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.User;
import com.researchspace.model.record.AbstractForm;
import com.researchspace.model.record.FormUserMenu;
import com.researchspace.model.record.RSForm;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository("formCreateMenuDao")
public class FormCreateDaoHibernateImpl extends GenericDaoHibernate<FormUserMenu, Long>
    implements FormCreateMenuDao {

  public FormCreateDaoHibernateImpl() {
    super(FormUserMenu.class);
  }

  @Override
  public boolean removeForUserAndForm(User user, String stableID) {
    int removed =
        getSession()
            .createQuery(
                "delete from FormUserMenu  menu where  menu.user=:user and"
                    + " menu.formStableId=:stableId")
            .setParameter("user", user)
            .setParameter("stableId", stableID)
            .executeUpdate();
    return removed == 1;
  }

  @Override
  public boolean formExistsInMenuForUser(String formStableID, User user) {
    List<String> inMenu =
        getSession()
            .createQuery(
                "select menu.formStableId from FormUserMenu menu where menu.formStableId ="
                    + " :formStableId and menu.user=:user",
                String.class)
            .setParameter("formStableId", formStableID)
            .setParameter("user", user)
            .list();
    return inMenu.size() > 0;
  }

  public void updateFormsInMenuForUser(User user, List<RSForm> allForms) {
    if (allForms.isEmpty()) {
      return;
    }
    List<String> ids = TransformerUtils.transformToString(allForms, "stableID");
    List<String> inMenu =
        getSession()
            .createQuery(
                "select menu.formStableId from FormUserMenu menu where menu.formStableId in :ids"
                    + " and menu.user=:user",
                String.class)
            .setParameterList("ids", ids)
            .setParameter("user", user)
            .list();
    for (AbstractForm form : allForms) {
      for (String menu : inMenu) {
        if (form.getStableID().equals(menu)) {
          form.setInSubjectMenu(true, user);
        }
      }
    }
  }
}
