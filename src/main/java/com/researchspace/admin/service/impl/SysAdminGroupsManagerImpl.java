package com.researchspace.admin.service.impl;

import com.researchspace.admin.service.GroupUsageInfo;
import com.researchspace.admin.service.SysadminGroupManager;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.CommunityDao;
import com.researchspace.dao.DatabaseUsageByGroupGroupByResult;
import com.researchspace.dao.FileMetadataDao;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.dtos.GroupSearchCriteria;
import com.researchspace.service.GroupManager;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("sysAdminGroupsManager")
@Slf4j
public class SysAdminGroupsManagerImpl extends AbstractSysadminMgr implements SysadminGroupManager {

  private @Autowired FileMetadataDao fileDao;
  private @Autowired CommunityDao communityDao;
  private @Autowired GroupManager grpMgr;

  @Override
  public ISearchResults<GroupUsageInfo> getGroupUsageInfo(
      User admin, PaginationCriteria<Group> pgCrit) {
    checkArgs(admin, pgCrit);
    List<GroupUsageInfo> groupinfo = new ArrayList<>();
    log.info("Calculating total file usage");
    Long totalUsage = fileDao.getTotalFileUsage();
    if (pgCrit.isOrderByAnInstanceProperty() || "owner.lastName".equals(pgCrit.getOrderBy())) {
      log.info("Ordering by username or group property.. {}", pgCrit.getOrderBy());
      return doStandardGroupBasedList(admin, pgCrit, groupinfo, totalUsage);
    } else if ("usage".equalsIgnoreCase(pgCrit.getOrderBy())) {
      log.info("Ordering by usage..");
      // do db search for file usage
      // restrict by community
      if (isCommunityAdmin(admin)) {
        log.info("Listing communities for community admin...");
        List<Community> communities = communityDao.listCommunitiesForAdmin(admin.getId());
        // admin user is not in community, return empty list
        if (communities.isEmpty()) {
          return new SearchResultsImpl<GroupUsageInfo>(Collections.emptyList(), pgCrit, 0L);
        } else {
          Community comm = communities.get(0);
          if (pgCrit.getSearchCriteria() == null) {
            // 1.53 in practice this never seems to be the case as is created in the controller
            GroupSearchCriteria srchCriteria = new GroupSearchCriteria();
            srchCriteria.setFilterByCommunity(true);
            srchCriteria.setCommunityId(comm.getId());
            srchCriteria.setLoadCommunity(true);
            pgCrit.setSearchCriteria(srchCriteria);
          } else {
            ((GroupSearchCriteria) pgCrit.getSearchCriteria()).setCommunityId(comm.getId());
          }
        }
      }
      log.info("Getting total file usage for lab groups");
      ISearchResults<DatabaseUsageByGroupGroupByResult> fileUsage =
          fileDao.getTotalFileUsageForLabGroups(pgCrit);
      List<Long> groupIds =
          fileUsage.getResults().stream()
              .map((t) -> t.getGroupId().longValue())
              .collect(Collectors.toList());

      // now get groups from ids.

      List<Group> grps = grpMgr.getGroups(groupIds);
      for (DatabaseUsageByGroupGroupByResult groupUsage : fileUsage.getResults()) {
        Group grp = findGrpForUsage(grps, groupUsage);
        GroupUsageInfo info =
            new GroupUsageInfo(grp, groupUsage.getFileusage().longValue(), totalUsage);
        groupinfo.add(info);
      }
      return createGrpSearchResults(groupinfo, fileUsage);
    } else {
      log.warn(
          "Unknown order by setting [{}], reverting to default 'displayName'", pgCrit.getOrderBy());
      pgCrit.setOrderBy(Group.DEFAULT_ORDERBY_FIELD);
      return doStandardGroupBasedList(admin, pgCrit, groupinfo, totalUsage);
    }
  }

  private boolean isCommunityAdmin(User admin) {
    return admin.hasRole(Role.ADMIN_ROLE) && !(admin.hasRole(Role.SYSTEM_ROLE));
  }

  private ISearchResults<GroupUsageInfo> doStandardGroupBasedList(
      User admin,
      PaginationCriteria<Group> pgCrit,
      List<GroupUsageInfo> groupinfo,
      Long totalUsage) {
    log.info("Listing groups");
    ISearchResults<Group> results = grpMgr.list(admin, pgCrit);

    addGroupUsageInfo(groupinfo, totalUsage, results.getResults(), pgCrit);
    return createGrpSearchResults(groupinfo, results);
  }

  private void addGroupUsageInfo(
      List<GroupUsageInfo> groupinfo,
      Long totalUsage,
      List<Group> results,
      PaginationCriteria<Group> pgCrit) {
    log.info("Calculating group file usage info - total usage is {}", totalUsage);
    List<DatabaseUsageByGroupGroupByResult> groupUsage =
        fileDao.getTotalFileUsageForGroups(results);
    log.info("Transforming results for {} groupUsage results", groupUsage.size());
    _addGroupInfo(groupinfo, totalUsage, results, groupUsage);
  }

  private void _addGroupInfo(
      List<GroupUsageInfo> groupinfo,
      final Long totalUsage,
      List<Group> results,
      List<DatabaseUsageByGroupGroupByResult> groupUsage) {
    for (Group grp : results) {
      DatabaseUsageByGroupGroupByResult res = findResultForGroup(grp, groupUsage);
      GroupUsageInfo info = new GroupUsageInfo(grp, res.getFileusage().longValue(), totalUsage);
      groupinfo.add(info);
    }
  }

  private DatabaseUsageByGroupGroupByResult findResultForGroup(
      Group grp, List<DatabaseUsageByGroupGroupByResult> groupinfo) {
    for (DatabaseUsageByGroupGroupByResult usage : groupinfo) {
      if (usage.getGroupId().longValue() == grp.getId()) {
        return usage;
      }
    }
    // else rturn empty result if no files present for group.
    return new DatabaseUsageByGroupGroupByResult(new BigInteger(grp.getId() + ""), 0d);
  }

  private Group findGrpForUsage(List<Group> grps, DatabaseUsageByGroupGroupByResult groupUsage) {
    for (Group grp : grps) {
      if (grp.getId().equals(groupUsage.getGroupId().longValue())) {
        return grp;
      }
    }
    return null; // should never happen
  }

  private SearchResultsImpl<GroupUsageInfo> createGrpSearchResults(
      List<GroupUsageInfo> uui, ISearchResults<?> grpList) {
    return new SearchResultsImpl<GroupUsageInfo>(
        uui, grpList.getPageNumber(), grpList.getTotalHits(), grpList.getHitsPerPage());
  }
}
