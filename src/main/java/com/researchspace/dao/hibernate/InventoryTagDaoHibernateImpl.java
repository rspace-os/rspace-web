package com.researchspace.dao.hibernate;

import com.researchspace.dao.InventoryTagDao;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryTagDaoHibernateImpl extends InventoryDaoHibernate<InventoryRecord, Long>
    implements InventoryTagDao {

  public InventoryTagDaoHibernateImpl() {
    super(InventoryRecord.class);
  }

  public List<String> getTagsForUser(User user) {
    // prepare owner and permission limiting query fragment
    List<String> userGroupMembers =
        invPermissionUtils.getUsernameOfUserAndAllMembersOfTheirGroups(user);
    List<String> userGroupsUniqueNames =
        user.getGroups().stream().map(Group::getUniqueName).collect(Collectors.toList());
    List<String> visibleOwners = invPermissionUtils.getOwnersVisibleWithUserRole(user);
    String ownedByAndPermittedItemsQueryFragment =
        getOwnedByAndPermittedItemsSqlQueryFragment(
            null, user, userGroupMembers, userGroupsUniqueNames, visibleOwners);

    Set<String> allTags = new HashSet<>();
    for (String targetClass : List.of("Sample", "SubSample", "Container")) {
      allTags.addAll(
          getTagsForTargetClass(
              targetClass,
              user,
              userGroupMembers,
              userGroupsUniqueNames,
              visibleOwners,
              ownedByAndPermittedItemsQueryFragment));
    }
    return new ArrayList<>(allTags);
  }

  private List<String> getTagsForTargetClass(
      String targetClass,
      User user,
      List<String> userGroupMembers,
      List<String> userGroupsUniqueNames,
      List<String> visibleOwners,
      String ownedByAndPermittedItemsQueryFragment) {
    int startPosition = 0;
    int maxResult = 1000000;
    if (targetClass.equals("SubSample")) {
      ownedByAndPermittedItemsQueryFragment =
          ownedByAndPermittedItemsQueryFragment.replaceAll(
              "owner.username", "SubSample.sample.owner.username");
      targetClass = targetClass + " " + targetClass;
    }
    Query<String> allTagsQueryBase =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select distinct tagMetaData from "
                    + targetClass
                    + " where deleted=false  and tagMetaData is not null "
                    + ownedByAndPermittedItemsQueryFragment,
                String.class)
            .setFirstResult(startPosition)
            .setMaxResults(maxResult);
    Query<String> allTagsQueryWithParams =
        addQueryParams(
            null, user, allTagsQueryBase, visibleOwners, userGroupMembers, userGroupsUniqueNames);
    return allTagsQueryWithParams.list();
  }
}
