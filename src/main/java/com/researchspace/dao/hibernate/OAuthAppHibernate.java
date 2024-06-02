package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.OAuthAppDao;
import com.researchspace.model.oauth.OAuthApp;
import java.util.List;
import java.util.Optional;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class OAuthAppHibernate extends GenericDaoHibernate<OAuthApp, Long> implements OAuthAppDao {
  public OAuthAppHibernate() {
    super(OAuthApp.class);
  }

  @Override
  public Optional<OAuthApp> getApp(Long userId, String clientId) {
    Query<OAuthApp> q =
        getSession()
            .createQuery(
                "from OAuthApp app where app.user.id=:userId and clientId=:clientId",
                OAuthApp.class);
    q.setParameter("userId", userId);
    q.setParameter("clientId", clientId);

    return q.uniqueResultOptional();
  }

  @Override
  public Optional<OAuthApp> getApp(String clientId) {
    Query<OAuthApp> q =
        getSession().createQuery("from OAuthApp where clientId=:clientId", OAuthApp.class);
    q.setParameter("clientId", clientId);

    return q.uniqueResultOptional();
  }

  @Override
  public List<OAuthApp> getApps(Long userId) {
    Query<OAuthApp> q =
        getSession().createQuery("from OAuthApp app where app.user.id=:userId", OAuthApp.class);
    q.setParameter("userId", userId);

    return q.list();
  }

  @Override
  public boolean removeApp(Long userId, String clientId) {
    Query<?> q =
        getSession()
            .createQuery(
                "delete from OAuthApp app where app.user.id=:userId and clientId=:clientId");
    q.setParameter("userId", userId);
    q.setParameter("clientId", clientId);

    int appsRemoved = q.executeUpdate();

    return appsRemoved == 1;
  }
}
