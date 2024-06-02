package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.UserConnectionDao;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import java.util.Optional;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class UserConnectionDaoHibernate
    extends GenericDaoHibernate<UserConnection, UserConnectionId> implements UserConnectionDao {

  private @Autowired com.researchspace.model.permissions.TextEncryptor textEncryptor;

  public UserConnectionDaoHibernate() {
    super(UserConnection.class);
  }

  @Override
  public Optional<UserConnection> findByUserNameProviderName(
      String rspaceUserName, String providerName) {
    Query<UserConnection> q =
        getSession()
            .createQuery(
                "from UserConnection where userId=:userId and providerId=:providerId",
                UserConnection.class);
    q.setReadOnly(true);
    Optional<UserConnection> res =
        q.setParameter("userId", rspaceUserName)
            .setParameter("providerId", providerName)
            .uniqueResultOptional();
    return res.map(this::decrypt);
  }

  private UserConnection decrypt(UserConnection uc) {
    return uc.decryptTokens(textEncryptor);
  }

  @Override
  public int deleteByUserAndProvider(String providername, String rspaceUserName) {
    return getSession()
        .createQuery("delete from UserConnection where userId=:userId and providerId=:providerId")
        .setParameter("userId", rspaceUserName)
        .setParameter("providerId", providername)
        .executeUpdate();
  }

  public UserConnection save(UserConnection uc) {
    uc.encryptTokens(textEncryptor);
    UserConnection saved = super.save(uc);
    saved.setTransientlyEncrypted(uc.isTransientlyEncrypted());
    return saved;
  }
}
