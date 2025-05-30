package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.OAuthTokenDao;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.oauth.OAuthTokenType;
import java.util.List;
import java.util.Optional;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class OAuthTokenHibernate extends GenericDaoHibernate<OAuthToken, Long>
    implements OAuthTokenDao {
  public OAuthTokenHibernate() {
    super(OAuthToken.class);
  }

  @Override
  public Optional<OAuthToken> findByAccessTokenHash(String searchHash) {
    Query<OAuthToken> q =
        getSession().createQuery("from OAuthToken where accessToken=:searchHash", OAuthToken.class);
    q.setParameter("searchHash", searchHash);
    return q.uniqueResultOptional();
  }

  @Override
  public Optional<OAuthToken> findByRefreshTokenHash(String searchHash) {
    Query<OAuthToken> q =
        getSession()
            .createQuery("from OAuthToken where refreshToken=:searchHash", OAuthToken.class);
    q.setParameter("searchHash", searchHash);
    return q.uniqueResultOptional();
  }

  @Override
  public List<OAuthToken> listTokensForUser(Long userId) {
    Query<OAuthToken> q =
        getSession()
            .createQuery("from OAuthToken token where token.user.id=:userId", OAuthToken.class);
    q.setParameter("userId", userId);
    return q.list();
  }

  @Override
  public List<OAuthToken> listTokensForClient(String clientId) {
    Query<OAuthToken> q =
        getSession().createQuery("from OAuthToken where clientId=:clientId", OAuthToken.class);
    q.setParameter("clientId", clientId);
    return q.list();
  }

  @Override
  public Optional<OAuthToken> getToken(String clientId, Long userId, OAuthTokenType tokenType) {
    Query<OAuthToken> q =
        getSession()
            .createQuery(
                "from OAuthToken token where token.user.id=:userId and clientId=:clientId "
                    + "and tokenType=:tokenType",
                OAuthToken.class);
    q.setParameter("userId", userId);
    q.setParameter("clientId", clientId);
    q.setParameter("tokenType", tokenType);
    return q.uniqueResultOptional();
  }

  @Override
  public int removeAllTokens(String clientId) {
    Query<?> q = getSession().createQuery("delete from OAuthToken where clientId=:clientId");
    q.setParameter("clientId", clientId);
    return q.executeUpdate();
  }
}
