package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.UserConnectionDao;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import java.util.List;
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

  @Override
  public Optional<UserConnection> findByUserNameProviderName(
      String rspaceUserName, String providerName, String discriminant) {
    Query<UserConnection> q =
        getSession()
            .createQuery(
                "from UserConnection where userId=:userId "
                    + " and providerId=:providerId and providerUserId=:discriminant",
                UserConnection.class);
    q.setReadOnly(true);
    Optional<UserConnection> res =
        q.setParameter("userId", rspaceUserName)
            .setParameter("providerId", providerName)
            .setParameter("discriminant", discriminant)
            .uniqueResultOptional();
    return res.map(this::decrypt);
  }

  private UserConnection decrypt(UserConnection uc) {
    return uc.decryptTokens(textEncryptor);
  }

  private List<UserConnection> decrypt(List<UserConnection> userConnectionList) {
    for (UserConnection userConnection : userConnectionList) {
      userConnection.decryptTokens(textEncryptor);
    }
    return userConnectionList;
  }

  @Override
  public int deleteByUserAndProvider(String rspaceUserName, String providername) {
    return getSession()
        .createQuery("delete from UserConnection where userId=:userId and providerId=:providerId")
        .setParameter("userId", rspaceUserName)
        .setParameter("providerId", providername)
        .executeUpdate();
  }

  @Override
  public int deleteByUserAndProvider(
      String rspaceUserName, String providername, String discriminant) {
    return getSession()
        .createQuery(
            "delete from UserConnection where userId=:userId "
                + " and providerId=:providerId and providerUserId=:discriminant")
        .setParameter("userId", rspaceUserName)
        .setParameter("providerId", providername)
        .setParameter("discriminant", discriminant)
        .executeUpdate();
  }

  @Override
  public Optional<Integer> findMaxRankByUserNameProviderName(
      String rspaceUserName, String providerName) {
    return getSession()
        .createQuery(
            "select max(rank) from UserConnection where userId=:userId "
                + " and providerId=:providerId")
        .setParameter("userId", rspaceUserName)
        .setParameter("providerId", providerName)
        .uniqueResultOptional();
  }

  @Override
  public List<UserConnection> findListByUserNameProviderName(
      String rspaceUserName, String providerName) {
    Query<UserConnection> q =
        getSession()
            .createQuery(
                "from UserConnection where userId=:userId " + " and providerId=:providerId");
    q.setReadOnly(true);
    List<UserConnection> res =
        q.setParameter("userId", rspaceUserName)
            .setParameter("providerId", providerName)
            .getResultList();

    return decrypt(res);
  }

  public UserConnection save(UserConnection uc) {
    uc.encryptTokens(textEncryptor);
    UserConnection saved = super.save(uc);
    saved.setTransientlyEncrypted(uc.isTransientlyEncrypted());
    return saved;
  }
}
