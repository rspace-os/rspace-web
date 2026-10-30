package com.researchspace.admin.service.impl;

import com.researchspace.admin.service.IServerlogRetriever;
import com.researchspace.admin.service.SysAdminManager;
import com.researchspace.admin.service.UserPublicInfoForUsageInfo;
import com.researchspace.admin.service.UserUsageInfo;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.*;
import com.researchspace.model.Community;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.sort.UserSort;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("sysAdminManager")
public class SysAdminManagerImpl extends AbstractSysadminMgr implements SysAdminManager {

  private @Autowired UserDao userDao;
  private @Autowired FileMetadataDao fileDao;
  private @Autowired IServerlogRetriever serverLogRetriever;
  private @Autowired CommunityDao commDao;
  private @Autowired RecordDao recordDao;

  @Override
  public ISearchResults<UserUsageInfo> getUserUsageInfo(
      User sysadmin, PaginationCriteria<User> pgCrit) {

    checkArgs(sysadmin, pgCrit);

    Community communityToLimitTo = null;
    ISearchResults<User> commUsers = null;
    if (!sysadmin.hasRole(Role.SYSTEM_ROLE)) {
      communityToLimitTo = getCommunityForAdmin(sysadmin, communityToLimitTo);
      if (communityToLimitTo == null) {
        return getEmptyUserUsageResults(pgCrit);
      }
      commUsers = getAllUsersInCommunity(communityToLimitTo);
      if (commUsers.getHits() == 0) {
        return getEmptyUserUsageResults(pgCrit);
      }
    }

    List<UserUsageInfo> uui = new ArrayList<>();
    UserSort sort = UserSort.fromRequest(pgCrit.getOrderBy());
    if (sort != UserSort.FILE_USAGE && sort != UserSort.RECORD_COUNT) {
      ISearchResults<User> userDBList;
      if (communityToLimitTo != null) {
        userDBList =
            userDao.listUsersInCommunity(communityToLimitTo.getId(), pgCrit); // RSPACE ADMIN
      } else {
        userDBList = userDao.searchUsers(pgCrit); // SYSADMIN
      }
      if (userDBList.getHits() > 0) {
        Map<String, DatabaseUsageByUserGroupByResult> fileUsage =
            fileDao.getTotalFileUsageForUsers(userDBList.getResults(), pgCrit);
        Map<String, DatabaseUsageByUserGroupByResult> recordCounts =
            recordDao.getTotalRecordsForUsers(userDBList.getResults(), pgCrit);
        for (User user : userDBList.getResults()) {
          UserUsageInfo info =
              setUserUsageInfoProperties(
                  user, fileUsage.get(user.getUsername()), recordCounts.get(user.getUsername()));
          uui.add(info);
        }
      }
      return createSearchResults(uui, userDBList);
    }

    if (sort == UserSort.FILE_USAGE) {
      // we need to get ordered paginated list by fileusage, then get user information for each user
      Map<String, DatabaseUsageByUserGroupByResult> fileUsage;
      long totalUsersWithFiles;
      if (communityToLimitTo != null) {
        totalUsersWithFiles = commUsers.getTotalHits();
        fileUsage = fileDao.getTotalFileUsageForUsers(commUsers.getResults(), pgCrit);
      } else {
        totalUsersWithFiles = fileDao.getCountOfUsersWithFilesInFileSystem();
        fileUsage = fileDao.getTotalFileUsageForAllUsers(pgCrit);
      }

      List<User> users = getUsersInOrder(fileUsage.keySet());
      Map<String, DatabaseUsageByUserGroupByResult> recordCounts =
          recordDao.getTotalRecordsForUsers(users, pgCrit);
      for (User user : users) {
        uui.add(
            setUserUsageInfoProperties(
                user, fileUsage.get(user.getUsername()), recordCounts.get(user.getUsername())));
      }
      return new SearchResultsImpl<>(
          uui, pgCrit.getPageNumber().intValue(), totalUsersWithFiles, pgCrit.getResultsPerPage());
    }

    // sort == UserSort.RECORD_COUNT
    Map<String, DatabaseUsageByUserGroupByResult> recordCount;
    long totalUsersWithRecords;
    if (communityToLimitTo != null) {
      recordCount = recordDao.getTotalRecordsForUsers(commUsers.getResults(), pgCrit);
      totalUsersWithRecords = commUsers.getTotalHits();
    } else {
      recordCount = recordDao.getTotalRecordsForUsers(pgCrit);
      totalUsersWithRecords = recordDao.getCountOfUsersWithRecords();
    }
    List<User> users = getUsersInOrder(recordCount.keySet());
    Map<String, DatabaseUsageByUserGroupByResult> fileUsage =
        fileDao.getTotalFileUsageForUsers(users, pgCrit);
    for (User user : users) {
      uui.add(
          setUserUsageInfoProperties(
              user, fileUsage.get(user.getUsername()), recordCount.get(user.getUsername())));
    }
    return new SearchResultsImpl<>(
        uui, pgCrit.getPageNumber().intValue(), totalUsersWithRecords, pgCrit.getResultsPerPage());
  }

  private ISearchResults<User> getAllUsersInCommunity(Community comm) {
    PaginationCriteria<User> pgCritTemp = new PaginationCriteria<>();
    pgCritTemp.setGetAllResults();
    ISearchResults<User> commUsers = userDao.listUsersInCommunity(comm.getId(), pgCritTemp);
    return commUsers;
  }

  private SearchResultsImpl<UserUsageInfo> getEmptyUserUsageResults(
      PaginationCriteria<User> pgCrit) {
    return new SearchResultsImpl<>(Collections.emptyList(), pgCrit.getPageNumber().intValue(), 0L);
  }

  private UserUsageInfo setUserUsageInfoProperties(
      User user,
      DatabaseUsageByUserGroupByResult fileUsageCount,
      DatabaseUsageByUserGroupByResult recordCount) {
    return UserUsageInfo.builder()
        .creationDate(user.getCreationDate())
        .fileUsage(fileUsageCount != null ? fileUsageCount.getUsage() : 0)
        .recordCount(recordCount != null ? recordCount.getUsage() : 0)
        .userInfo(new UserPublicInfoForUsageInfo(user))
        .signupSource(user.getSignupSource().toString())
        .lastLogin(user.getLastLogin())
        .build();
  }

  /** Keeps the order of {@code usernames}, which reflects the requested sort. */
  private List<User> getUsersInOrder(Collection<String> usernames) {
    Map<String, User> byUsername =
        userDao.getUsersByUsernames(usernames).stream()
            .collect(Collectors.toMap(User::getUsername, Function.identity()));
    return usernames.stream()
        .map(byUsername::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private Community getCommunityForAdmin(User sysadmin, Community comm) {
    List<Community> comms = commDao.listCommunitiesForAdmin(sysadmin.getId());
    if (!comms.isEmpty()) {
      comm = comms.get(0);
    }
    return comm;
  }

  private SearchResultsImpl<UserUsageInfo> createSearchResults(
      List<UserUsageInfo> uui, ISearchResults<User> userDBList) {
    return new SearchResultsImpl<>(
        uui, userDBList.getPageNumber(), userDBList.getTotalHits(), userDBList.getHitsPerPage());
  }

  @Override
  public List<String> getLastNLinesLogs(int maxLines) throws IOException {
    return serverLogRetriever.retrieveLastNLogLines(maxLines);
  }
}
