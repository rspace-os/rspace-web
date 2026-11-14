package com.researchspace.dao.hibernate;

import com.researchspace.dao.FeatureFlagDao;
import com.researchspace.featureflags.FeatureFlagBaseline;
import com.researchspace.featureflags.FeatureFlagUserOverride;
import com.researchspace.model.User;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.CacheMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

@Repository
public class FeatureFlagDaoHibernate implements FeatureFlagDao {

  private final SessionFactory sessionFactory;

  public FeatureFlagDaoHibernate(SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public Map<String, Boolean> getBaselineValues() {
    return getSession()
        .createQuery("from FeatureFlagBaseline", FeatureFlagBaseline.class)
        .list()
        .stream()
        .collect(
            Collectors.toMap(
                FeatureFlagBaseline::getFlagName,
                FeatureFlagBaseline::isEnabled,
                (existing, replacement) -> replacement,
                LinkedHashMap::new));
  }

  @Override
  public Optional<Boolean> getCurrentBaseline(String flagName) {
    return getSession()
        .createNativeQuery(
            "select enabled from FeatureFlagBaseline where flag_name = :flag lock in share mode",
            Boolean.class)
        .setParameter("flag", flagName)
        .uniqueResultOptional();
  }

  @Override
  public Optional<Boolean> getCurrentOverride(String flagName, Long userId) {
    return getSession()
        .createNativeQuery(
            "select enabled from FeatureFlagUserOverride where flag_name = :flag"
                + " and user_id = :userId lock in share mode",
            Boolean.class)
        .setParameter("flag", flagName)
        .setParameter("userId", userId)
        .uniqueResultOptional();
  }

  @Override
  public void upsertBaseline(String flagName, boolean enabled) {
    FeatureFlagBaseline baseline = getSession().get(FeatureFlagBaseline.class, flagName);
    if (baseline == null) {
      getSession().persist(new FeatureFlagBaseline(flagName, enabled));
    } else {
      baseline.setEnabled(enabled);
    }
  }

  @Override
  public int deleteBaselinesNotIn(Collection<String> flagNames) {
    if (flagNames.isEmpty()) {
      return getSession().createQuery("delete from FeatureFlagBaseline").executeUpdate();
    }
    return getSession()
        .createQuery("delete from FeatureFlagBaseline where flagName not in (:flagNames)")
        .setParameter("flagNames", flagNames)
        .executeUpdate();
  }

  @Override
  public int deleteOverridesNotIn(Collection<String> flagNames) {
    if (flagNames.isEmpty()) {
      return getSession().createQuery("delete from FeatureFlagUserOverride").executeUpdate();
    }
    return getSession()
        .createQuery("delete from FeatureFlagUserOverride where flagName not in (:flagNames)")
        .setParameter("flagNames", flagNames)
        .executeUpdate();
  }

  @Override
  public Map<String, Boolean> getOverridesForUser(Long userId) {
    return getSession()
        .createQuery(
            "from FeatureFlagUserOverride where user.id = :userId", FeatureFlagUserOverride.class)
        .setParameter("userId", userId)
        .list()
        .stream()
        .collect(
            Collectors.toMap(
                FeatureFlagUserOverride::getFlagName,
                FeatureFlagUserOverride::isEnabled,
                (existing, replacement) -> replacement,
                LinkedHashMap::new));
  }

  @Override
  public Map<Long, Boolean> getOverridesForUsers(String flagName, Set<Long> userIds) {
    if (userIds.isEmpty()) return Map.of();
    return getSession()
        .createQuery(
            "select o.user.id, o.enabled from FeatureFlagUserOverride o where o.flagName = :flag"
                + " and o.user.id in :ids",
            Object[].class)
        .setParameter("flag", flagName)
        .setParameterList("ids", userIds)
        .setCacheMode(CacheMode.IGNORE)
        .getResultStream()
        .collect(Collectors.toMap(row -> (Long) row[0], row -> (Boolean) row[1]));
  }

  @Override
  public void setOverride(Long userId, String flagName, boolean enabled) {
    FeatureFlagUserOverride override =
        getSession()
            .createQuery(
                "from FeatureFlagUserOverride where user.id = :userId and flagName = :flagName",
                FeatureFlagUserOverride.class)
            .setParameter("userId", userId)
            .setParameter("flagName", flagName)
            .uniqueResult();
    if (override == null) {
      User user = getSession().getReference(User.class, userId);
      getSession().persist(new FeatureFlagUserOverride(user, flagName, enabled));
    } else {
      override.setEnabled(enabled);
    }
  }

  @Override
  public void clearOverride(Long userId, String flagName) {
    getSession()
        .createQuery(
            "delete from FeatureFlagUserOverride where user.id = :userId and flagName = :flagName")
        .setParameter("userId", userId)
        .setParameter("flagName", flagName)
        .executeUpdate();
  }

  private Session getSession() {
    return sessionFactory.getCurrentSession();
  }
}
