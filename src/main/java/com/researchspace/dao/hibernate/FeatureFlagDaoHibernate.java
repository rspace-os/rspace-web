package com.researchspace.dao.hibernate;

import com.researchspace.dao.FeatureFlagDao;
import com.researchspace.featureflags.FeatureFlagBaseline;
import com.researchspace.featureflags.FeatureFlagUserOverride;
import com.researchspace.model.User;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class FeatureFlagDaoHibernate implements FeatureFlagDao {

  @Autowired private SessionFactory sessionFactory;

  @Override
  public Map<String, Boolean> getBaselineValues() {
    List<FeatureFlagBaseline> baselines =
        getSession().createQuery("from FeatureFlagBaseline", FeatureFlagBaseline.class).list();
    Map<String, Boolean> values = new LinkedHashMap<>();
    for (FeatureFlagBaseline baseline : baselines) {
      values.put(baseline.getFlagName(), baseline.isEnabled());
    }
    return values;
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
    List<FeatureFlagUserOverride> overrides =
        getSession()
            .createQuery(
                "from FeatureFlagUserOverride where user.id = :userId",
                FeatureFlagUserOverride.class)
            .setParameter("userId", userId)
            .list();
    Map<String, Boolean> values = new LinkedHashMap<>();
    for (FeatureFlagUserOverride override : overrides) {
      values.put(override.getFlagName(), override.isEnabled());
    }
    return values;
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
      User user = getSession().load(User.class, userId);
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
