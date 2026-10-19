package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.UserConnectionDao;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
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
                "from UserConnection where id.userId=:userId and id.providerId=:providerId",
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
                "from UserConnection where id.userId=:userId "
                    + " and id.providerId=:providerId and id.providerUserId=:discriminant",
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
    // Decrypted credentials must never be flushed back as plaintext, including entities
    // already loaded by an earlier save in the same transaction.
    getSession().setReadOnly(uc, true);
    return uc.decryptTokens(textEncryptor);
  }

  private List<UserConnection> decrypt(List<UserConnection> userConnectionList) {
    for (UserConnection userConnection : userConnectionList) {
      decrypt(userConnection);
    }
    return userConnectionList;
  }

  @Override
  public int deleteByUserAndProvider(String rspaceUserName, String providername) {
    return getSession()
        .createQuery(
            "delete from UserConnection where id.userId=:userId and id.providerId=:providerId")
        .setParameter("userId", rspaceUserName)
        .setParameter("providerId", providername)
        .executeUpdate();
  }

  @Override
  public int deleteByUserAndProvider(
      String rspaceUserName, String providername, String discriminant) {
    return getSession()
        .createQuery(
            "delete from UserConnection where id.userId=:userId "
                + " and id.providerId=:providerId and id.providerUserId=:discriminant")
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
            "select max(rank) from UserConnection where id.userId=:userId "
                + " and id.providerId=:providerId")
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
                "from UserConnection where id.userId=:userId " + " and id.providerId=:providerId");
    q.setReadOnly(true);
    List<UserConnection> res =
        q.setParameter("userId", rspaceUserName)
            .setParameter("providerId", providerName)
            .getResultList();

    return decrypt(res);
  }

  public UserConnection save(UserConnection uc) {
    uc.encryptTokens(textEncryptor);
    Session session = getSession();
    // UserConnection has @EmbeddedId (always non-null), so check if it already exists
    if (session.contains(uc) && !session.isReadOnly(uc)) {
      // already managed
      uc.setTransientlyEncrypted(uc.isTransientlyEncrypted());
      return uc;
    }
    if (session.contains(uc)) {
      session.evict(uc);
    }
    UserConnection existing = session.get(UserConnection.class, uc.getId());
    if (existing != null && session.isReadOnly(existing)) {
      // A read-only credential may also be a stale identity left after a bulk delete.
      session.evict(existing);
      existing = session.get(UserConnection.class, uc.getId());
    }
    UserConnection saved;
    if (existing == null) {
      session.persist(uc);
      saved = uc;
    } else {
      saved = (UserConnection) session.merge(uc);
    }
    saved.setTransientlyEncrypted(uc.isTransientlyEncrypted());
    return saved;
  }
}
