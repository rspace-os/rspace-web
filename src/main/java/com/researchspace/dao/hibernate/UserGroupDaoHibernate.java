package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.UserGroupDao;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class UserGroupDaoHibernate extends GenericDaoHibernate<UserGroup, Long>
    implements UserGroupDao {

  public UserGroupDaoHibernate() {
    super(UserGroup.class);
  }

  @Override
  public List<UserGroup> findByUserId(Long userId) {
    return getSession()
        .createQuery("from UserGroup ug where ug.user.id=:id", UserGroup.class)
        .setParameter("id", userId)
        .list();
  }

  @Override
  public boolean hasLockedTransferAuthority(User subject, User owner) {
    List<UserGroup> subjectMemberships =
        getSession()
            .createQuery(
                "from UserGroup membership "
                    + "where membership.user.id = :subjectId "
                    + "and membership.roleInGroup in :transferRoles",
                UserGroup.class)
            .setParameter("subjectId", subject.getId())
            .setParameterList("transferRoles", List.of(RoleInGroup.PI, RoleInGroup.RS_LAB_ADMIN))
            .setLockMode(LockModeType.PESSIMISTIC_READ)
            .getResultList();
    if (subjectMemberships.isEmpty()) {
      return false;
    }
    Set<Long> subjectGroupIds =
        subjectMemberships.stream()
            .map(membership -> membership.getGroup().getId())
            .collect(Collectors.toSet());
    return !getSession()
        .createQuery(
            "from UserGroup membership "
                + "where membership.user.id = :ownerId and membership.group.id in :groupIds",
            UserGroup.class)
        .setParameter("ownerId", owner.getId())
        .setParameterList("groupIds", subjectGroupIds)
        .setLockMode(LockModeType.PESSIMISTIC_READ)
        .getResultList()
        .isEmpty();
  }
}
