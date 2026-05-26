package com.researchspace.dao.hibernate;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import com.researchspace.Constants;
import com.researchspace.core.util.DateUtil;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.SortOrder;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.UserDao;
import com.researchspace.model.Community;
import com.researchspace.model.GroupType;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.SignupSource;
import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.User;
import com.researchspace.model.UserProfile;
import com.researchspace.model.dtos.UserRoleView;
import com.researchspace.model.dtos.UserSearchCriteria;
import com.researchspace.model.views.UserStatistics;
import com.researchspace.model.views.UserView;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.graph.EntityGraphs;
import org.hibernate.graph.GraphParser;
import org.hibernate.graph.RootGraph;
import org.hibernate.query.Query;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.stereotype.Repository;

/** This class retrieves User objects. */
@Repository("userDao")
public class UserDaoHibernate extends GenericDaoHibernate<User, Long> implements UserDao {

  private static final String QUERY_GROUPMEMBER_STRING =
      "select distinct ug2.user "
          + "from UserGroup ug2 where ug2.group.id in "
          + "( select group.id  from UserGroup ug where ug.user.id=:id "
          + "and ug.roleInGroup=:roleInGroup and group.groupType=:groupType )";
  private static final String ROLES = "roles";
  private static final String USERNAME = "username";
  private static final String USERS_IN_COMMUNITY_Q =
      " from User u inner join u.userGroups ug inner join ug.group g  inner join g.communities c "
          + " where c.id=:id ";
  private static final String SEARCH_QUERY_FORMAT =
      " and (u.email like '%%%s%%' or u.firstName like '%%%s%%' or u.lastName like '%%%s%%' or"
          + " u.username like '%%%s%%')";
  private static final String ORDER_BY_QUERY_FORMAT = " order by u.%s %s";
  private static final String ENABLED = "enabled";
  private static final String EMAIL = "email";
  private static final String FIRST_NAME = "firstName";
  private static final String LAST_NAME = "lastName";
  private static final String LAST_LOGIN = "lastLogin";
  private static final String TAGS = "tagsJsonString";

  /** Constructor that sets the entity to User.class. */
  public UserDaoHibernate() {
    super(User.class);
  }

  /** {@inheritDoc} */
  public List<User> getUsers() {
    Query<User> query =
        getSession()
            .createQuery(
                "from User user  where user.enabled=:enabled "
                    + "order by upper(user.lastName), upper(user.firstName)",
                User.class);
    return query.setParameter(ENABLED, true).list();
  }

  /** {@inheritDoc} */
  public User saveUser(User user) {
    log.debug("user's id: {}", user.getId());
    Session s = sessionFactory.getCurrentSession();
    s.saveOrUpdate(user);
    // this will force the throwing of a constraint violation exception
    // now, if there is one, so that it can be handled in the service layer.
    s.flush();
    return user;
  }

  /**
   * Overridden simply to call the saveUser method. This is happening because saveUser flushes the
   * session and saveObject of BaseDaoHibernate does not.
   *
   * @param user the user to save
   * @return the modified user (with a primary key set if they're new)
   */
  @Override
  public User save(User user) {
    return this.saveUser(user);
  }

  /** {@inheritDoc} */
  public String getUserPassword(String username) {
    Query<String> query =
        getSession()
            .createQuery(
                "select user.password from User user where user.username = :uname", String.class)
            .setParameter("uname", username);
    return query.uniqueResult();
  }

  public User getUserByUsername(String username) {
    CriteriaBuilder builder = getSession().getCriteriaBuilder();

    // eagerly fetch path usergroups.group.usergroups.user to load associated users in the same
    // groups as this user
    RootGraph<User> userGroups =
        GraphParser.parse(User.class, "userGroups(group(userGroups(user(roles))))", getSession());
    RootGraph<User> roles = GraphParser.parse(User.class, ROLES, getSession());

    RootGraph<User> graph =
        (RootGraph<User>)
            EntityGraphs.merge(getSession(), User.class, userGroups, (EntityGraph<User>) roles);

    CriteriaQuery<User> criteriaQuery = builder.createQuery(User.class);
    Root<User> root = criteriaQuery.from(User.class);

    criteriaQuery.select(root).where(builder.equal(root.get(USERNAME), username));

    Query<User> query = getSession().createQuery(criteriaQuery);
    query.applyFetchGraph(graph);
    User user = query.uniqueResult();

    if (user == null) {
      throw new ObjectRetrievalFailureException(User.class, username);
    } else {
      return user;
    }
  }

  @Override
  public Optional<User> getUserByUsernameAlias(String usernameAlias) {
    return getSession()
        .createQuery("from User where usernameAlias=:usernameAlias", User.class)
        .setParameter("usernameAlias", usernameAlias)
        .uniqueResultOptional();
  }

  public List<User> getUserByEmail(String email) {
    return getSession()
        .createQuery("from User where email=:email", User.class)
        .setParameter(EMAIL, email)
        .list();
  }

  @SuppressWarnings("unchecked")
  public List<User> searchUsers(String term) {
    String likeTerm = "%" + term.toLowerCase() + "%";
    return getSession()
        .createQuery(
            "select distinct u from User u "
                + "where lower(u.firstName) like :term "
                + "or lower(u.lastName) like :term "
                + "or lower(u.email) like :term "
                + "or lower(u.username) like :term "
                + "or lower(u.tagsJsonString) like :term "
                + "order by u.lastName asc",
            User.class)
        .setParameter("term", likeTerm)
        .list();
  }

  @Override
  public boolean isUserInAdminsCommunity(String usernameToTest, Long communityId) {
    String queryStr =
        "select  ug.user from Community c join c.labGroups groups  join groups.userGroups ug"
            + " where  c.id=:id and ug.user.username=:username";
    return !getSession()
        .createQuery(queryStr)
        .setParameter("id", communityId)
        .setParameter(USERNAME, usernameToTest)
        .list()
        .isEmpty();
  }

  @Override
  public ISearchResults<User> listUsersInCommunity(
      Long communityId, PaginationCriteria<User> pgCrit) {

    // in current codebase pgCrit is never null at this point, so let's make it explicit
    if (pgCrit == null) {
      throw new IllegalArgumentException("criteria are null");
    }

    Session session = getSessionFactory().getCurrentSession();
    String countQuery = "select count (distinct u) " + USERS_IN_COMMUNITY_Q;

    String subQuery = "";
    Map<String, Object> dateParams = new java.util.HashMap<>();
    if (pgCrit.getSearchCriteria() != null) {
      UserSearchCriteria searchCriteria = (UserSearchCriteria) pgCrit.getSearchCriteria();
      subQuery = applySearchRestrictionsToHQL(searchCriteria, dateParams);
      countQuery = countQuery + subQuery;
    }

    var countQ = session.createQuery(countQuery, Long.class).setParameter("id", communityId);
    dateParams.forEach(countQ::setParameter);
    Long userCount = countQ.uniqueResult();
    if (userCount == 0) {
      return new SearchResultsImpl<>(Collections.emptyList(), 0, 0L);
    }
    String orderBy = safeOrderBy(pgCrit);
    String listQuery = "select distinct u " + USERS_IN_COMMUNITY_Q + subQuery + orderBy;
    var listQ =
        session
            .createQuery(listQuery, User.class)
            .setParameter("id", communityId)
            .setMaxResults(pgCrit.getResultsPerPage())
            .setFirstResult(pgCrit.getFirstResultIndex());
    dateParams.forEach(listQ::setParameter);
    List<User> rc = listQ.list();
    return new SearchResultsImpl<>(
        rc, pgCrit.getPageNumber().intValue(), userCount, pgCrit.getResultsPerPage());
  }

  private String applySearchRestrictionsToHQL(UserSearchCriteria sc) {
    return applySearchRestrictionsToHQL(sc, null);
  }

  /**
   * @param params if non-null, date parameters will be added to this map (key=param name,
   *     value=Date) for use with query.setParameter()
   */
  private String applySearchRestrictionsToHQL(UserSearchCriteria sc, Map<String, Object> params) {
    var clauses = new ArrayList<String>();
    // this is for queries that are unreliable using Criteria. Functionality should be maintained
    // with
    // applySearchCriteria
    try {
      applySearchTermFieldsHQL(sc, clauses, params);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      log.error("Error applying search restrictions: {0}", e);
    }

    if (sc.isOnlyPublicProfiles()) {
      clauses.add("and u.privateProfile != true");
    }
    if (sc.isWithoutBackdoorSysadmins()) {
      clauses.add("and u.signupSource !=  '" + SignupSource.SSO_BACKDOOR + "'");
    }
    return StringUtils.join(clauses, " ");
  }

  private void applySearchTermFieldsHQL(
      UserSearchCriteria searchCriteria, ArrayList<String> clauses, Map<String, Object> params)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    Map<String, Object> key2Value = searchCriteria.getSearchTermField2Values();
    for (Entry<String, Object> entry : key2Value.entrySet()) {
      if ("allFields".equals(entry.getKey())) {
        String term = entry.getValue().toString();
        if (term != null && !term.equalsIgnoreCase("null")) {
          clauses.add(String.format(SEARCH_QUERY_FORMAT, term, term, term, term));
        }
      }

      if ("onlyEnabled".equals(entry.getKey())
          && "true".equalsIgnoreCase(entry.getValue().toString())) {
        clauses.add("and u.enabled = true");
      }
      if ("tempAccountsOnly".equals(entry.getKey())
          && "true".equalsIgnoreCase(entry.getValue().toString())) {
        clauses.add("and u.tempAccount = true");
      }
      if ("creationDateEarlierThan".equals(entry.getKey())) {
        parseDate(entry)
            .ifPresent(
                limit -> {
                  clauses.add("and u.creationDate < :creationDateLimit");
                  if (params != null) {
                    params.put("creationDateLimit", limit);
                  }
                });
      }
      if ("lastLoginEarlierThan".equals(entry.getKey())) {
        parseDate(entry)
            .ifPresent(
                limit -> {
                  clauses.add("and (u.lastLogin IS NULL or u.lastLogin < :lastLoginLimit)");
                  if (params != null) {
                    params.put("lastLoginLimit", limit);
                  }
                });
      }
    }
  }

  private String safeOrderBy(PaginationCriteria<User> pgCrit) {
    if (!StringUtils.isBlank(pgCrit.getOrderBy()) && pgCrit.isOrderBySafe(pgCrit.getOrderBy())) {
      return String.format(
          ORDER_BY_QUERY_FORMAT, pgCrit.getOrderBy(), pgCrit.getSortOrder().toString());
    } else {
      return "";
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public ISearchResults<User> searchUsers(PaginationCriteria<User> pgCrit) {

    // in current codebase pgCrit is never null at this point, so let's make it explicit
    if (pgCrit == null) {
      throw new IllegalArgumentException("criteria are null");
    }

    Session session = getSessionFactory().getCurrentSession();
    CriteriaBuilder builder = session.getCriteriaBuilder();

    CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
    Root<User> countRoot = countQuery.from(User.class);
    List<Predicate> countPredicates = buildUserPredicates(pgCrit, builder, countRoot);
    countQuery.select(builder.countDistinct(countRoot.get("id")));
    if (!countPredicates.isEmpty()) {
      countQuery.where(countPredicates.toArray(new Predicate[0]));
    }
    Long count = session.createQuery(countQuery).getSingleResult();
    if (count == 0) {
      return createEmptyResultSet(pgCrit);
    }

    CriteriaQuery<Long> idQuery = builder.createQuery(Long.class);
    Root<User> idRoot = idQuery.from(User.class);
    List<Predicate> idPredicates = buildUserPredicates(pgCrit, builder, idRoot);
    idQuery.select(idRoot.get("id")).distinct(true);
    if (!idPredicates.isEmpty()) {
      idQuery.where(idPredicates.toArray(new Predicate[0]));
    }
    applyUserOrder(pgCrit, builder, idRoot, idQuery);
    List<Long> ids =
        session
            .createQuery(idQuery)
            .setFirstResult(pgCrit.getFirstResultIndex())
            .setMaxResults(pgCrit.getResultsPerPage())
            .list();
    if (ids.isEmpty()) {
      log.warn("User count was > 0, but no users returned from query");
      return createEmptyResultSet(pgCrit);
    }

    CriteriaQuery<User> fetchQuery = builder.createQuery(User.class);
    Root<User> fetchRoot = fetchQuery.from(User.class);
    fetchQuery.select(fetchRoot).distinct(true);
    fetchQuery.where(fetchRoot.get("id").in(ids));
    applyUserOrder(pgCrit, builder, fetchRoot, fetchQuery);
    List<User> rc = session.createQuery(fetchQuery).list();
    return new SearchResultsImpl<>(rc, pgCrit, count);
  }

  private List<Predicate> buildUserPredicates(
      PaginationCriteria<User> pgCrit, CriteriaBuilder builder, Root<User> root) {
    List<Predicate> predicates = new ArrayList<>();
    predicates.add(builder.notEqual(root.get(USERNAME), RecordGroupSharing.ANONYMOUS_USER));
    if (pgCrit.getSearchCriteria() != null) {
      UserSearchCriteria sc = (UserSearchCriteria) pgCrit.getSearchCriteria();
      try {
        applySearchTermPredicates(sc, builder, root, predicates);
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        log.error("Error while applying search criteria: {0}", e);
      }

      if (sc.isOnlyPublicProfiles()) {
        predicates.add(builder.notEqual(root.get("privateProfile"), true));
      }
      if (sc.isWithoutBackdoorSysadmins()) {
        predicates.add(builder.notEqual(root.get("signupSource"), SignupSource.SSO_BACKDOOR));
      }
    }
    return predicates;
  }

  private void applySearchTermPredicates(
      UserSearchCriteria searchCriteria,
      CriteriaBuilder builder,
      Root<User> root,
      List<Predicate> predicates)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    Map<String, Object> key2Value = searchCriteria.getSearchTermField2Values();
    for (Entry<String, Object> entry : key2Value.entrySet()) {
      if ("allFields".equals(entry.getKey())) {
        String term = entry.getValue().toString().toLowerCase();
        Predicate allFieldsPredicate =
            builder.or(
                builder.like(builder.lower(root.get(FIRST_NAME)), "%" + term + "%"),
                builder.like(builder.lower(root.get(LAST_NAME)), "%" + term + "%"),
                builder.like(builder.lower(root.get(EMAIL)), "%" + term + "%"),
                builder.like(builder.lower(root.get(USERNAME)), "%" + term + "%"),
                builder.like(builder.lower(root.get(TAGS)), "%" + term + "%"));
        predicates.add(allFieldsPredicate);
      }
      if ("tags".equals(entry.getKey())) {
        if (entry.getValue() != null) {
          String[] tags = (String[]) entry.getValue();
          if (tags.length > 0) {
            List<Predicate> tagPredicates = new ArrayList<>();
            for (String tag : tags) {
              tagPredicates.add(
                  builder.like(builder.lower(root.get(TAGS)), "%\"" + tag.toLowerCase() + "\"%"));
            }
            predicates.add(builder.and(tagPredicates.toArray(new Predicate[0])));
          }
        }
      }

      if ("onlyEnabled".equals(entry.getKey())
          && "true".equalsIgnoreCase(entry.getValue().toString())) {
        predicates.add(builder.equal(root.get(ENABLED), true));
      }
      if ("tempAccountsOnly".equals(entry.getKey())
          && "true".equalsIgnoreCase(entry.getValue().toString())) {
        predicates.add(builder.equal(root.get("tempAccount"), true));
      }
      if ("creationDateEarlierThan".equals(entry.getKey())) {
        parseDate(entry)
            .ifPresent(limit -> predicates.add(builder.lessThan(root.get("creationDate"), limit)));
      }
      if ("lastLoginEarlierThan".equals(entry.getKey())) {
        parseDate(entry)
            .ifPresent(
                limit ->
                    predicates.add(
                        builder.or(
                            builder.lessThan(root.get(LAST_LOGIN), limit),
                            builder.isNull(root.get(LAST_LOGIN)))));
      }
    }
  }

  private Optional<Date> parseDate(Entry<String, Object> entry) {
    try {
      LocalDate localDate =
          LocalDate.parse(entry.getValue().toString(), DateTimeFormatter.ISO_DATE);
      Date limit = DateUtil.localDateToDateUTC(localDate);
      return Optional.of(limit);
    } catch (DateTimeParseException e) {
      log.warn("Couldn't parse date argument {} - ignoring", entry.getValue());
      return Optional.empty();
    }
  }

  private void applyUserOrder(
      PaginationCriteria<User> pgCrit,
      CriteriaBuilder builder,
      Root<User> root,
      CriteriaQuery<?> query) {
    if (pgCrit.getOrderBy() == null || !pgCrit.isOrderBySafe(pgCrit.getOrderBy())) {
      return;
    }
    List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
    if (SortOrder.ASC.equals(pgCrit.getSortOrder())) {
      orders.add(builder.asc(root.get(pgCrit.getOrderBy())));
      if (pgCrit.getOrderBy().equalsIgnoreCase(LAST_NAME)) {
        orders.add(builder.asc(root.get(FIRST_NAME)));
        orders.add(builder.asc(root.get(LAST_NAME)));
      }
    } else {
      orders.add(builder.desc(root.get(pgCrit.getOrderBy())));
      if (pgCrit.getOrderBy().equalsIgnoreCase(LAST_NAME)) {
        orders.add(builder.desc(root.get(FIRST_NAME)));
        orders.add(builder.desc(root.get(LAST_NAME)));
      }
    }
    query.orderBy(orders);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Set<User> getAllGroupPis(String searchTerm) {
    Session session = getSessionFactory().getCurrentSession();
    List<User> pis =
        session
            .createQuery(
                "select distinct u from User u join u.userGroups ug "
                    + "where ug.roleInGroup = :roleInGroup and u.enabled = true",
                User.class)
            .setParameter("roleInGroup", RoleInGroup.PI)
            .list();
    if (searchTerm != null) {
      List<User> matchingUsers = searchUsers(searchTerm);
      pis.retainAll(matchingUsers);
    }
    return new TreeSet<>(pis);
  }

  @Override
  public TokenBasedVerification saveTokenBasedVerification(TokenBasedVerification upwChange) {
    return (TokenBasedVerification) getSessionFactory().getCurrentSession().merge(upwChange);
  }

  @Override
  public TokenBasedVerification getByToken(String token) {
    Session session = getSessionFactory().getCurrentSession();
    return (TokenBasedVerification)
        session
            .createQuery("from TokenBasedVerification  where token=:token")
            .setParameter("token", token)
            .uniqueResult();
  }

  @Override
  public Optional<String> getUsernameByToken(String token) {
    return getSession()
        .createQuery(
            "select u.username from User u inner join TokenBasedVerification tbv on"
                + " u.email=tbv.email where tbv.token=:token",
            String.class)
        .setParameter("token", token)
        .uniqueResultOptional();
  }

  @Override
  public UserProfile getUserProfileByUser(User user) {
    return getSession()
        .createQuery("from UserProfile  where owner=:owner", UserProfile.class)
        .setParameter("owner", user)
        .uniqueResult();
  }

  @Override
  public UserProfile saveUserProfile(UserProfile profile) {
    Session session = getSession();
    Object id = session.getSessionFactory().getPersistenceUnitUtil().getIdentifier(profile);
    if (id == null) {
      session.persist(profile);
      return profile;
    }
    return (UserProfile) session.merge(profile);
  }

  @Override
  public UserProfile getUserProfileById(Long profileId) {
    return getSession()
        .createQuery("from UserProfile  where id=:id", UserProfile.class)
        .setParameter("id", profileId)
        .uniqueResult();
  }

  @SuppressWarnings("unchecked")
  @Override
  public ISearchResults<User> listUsersByRole(Role role, PaginationCriteria<User> pgCrit) {
    Session session = getSessionFactory().getCurrentSession();
    Long count = countUsersInRole(role, session, false);
    if (count == 0) {
      return new SearchResultsImpl<>(
          Collections.emptyList(), pgCrit.getPageNumber().intValue(), 0L);
    }

    String orderBy = safeOrderBy(pgCrit);
    Query<User> listQuery =
        session.createQuery(
            "select distinct u from User u join u.roles role "
                + "where role.name=:roleName"
                + orderBy,
            User.class);
    listQuery.setParameter("roleName", role.getName());
    listQuery.setFirstResult(pgCrit.getFirstResultIndex());
    listQuery.setMaxResults(pgCrit.getResultsPerPage());
    List<User> listQueryRes = listQuery.list();

    return new SearchResultsImpl<>(listQueryRes, pgCrit.getPageNumber().intValue(), count);
  }

  private Long countUsersInRole(Role role, Session session, boolean enabledOnly) {
    StringBuilder hql =
        new StringBuilder(
            "select count(distinct u.id) from User u join u.roles role where role.name=:roleName");
    if (enabledOnly) {
      hql.append(" and u.enabled = true");
    }
    return session
        .createQuery(hql.toString(), Long.class)
        .setParameter("roleName", role.getName())
        .uniqueResult();
  }

  @Override
  public List<User> getAvailableAdminsForCommunity() {

    // look for admin uses who are NOT currently in community admin list
    Query<User> q =
        getSession()
            .createQuery(
                " select user from User user join user.roles role "
                    + "where role.name=:role_name and user.id not in "
                    + "( select admin.id from Community comm  join comm.admins  admin )"
                    + " and user.accountLocked = :locked and user.enabled = :enabled",
                User.class);
    q.setParameter("locked", false);
    q.setParameter(ENABLED, true);
    q.setParameter("role_name", Constants.ADMIN_ROLE);
    return q.list();
  }

  @Override
  public boolean userExists(String username) {
    // load up id, not whole user graph to check existence
    return getSession()
            .createQuery("select u.id from User u where u.username=:username", Long.class)
            .setParameter(USERNAME, username)
            .setMaxResults(1)
            .uniqueResult()
        != null;
  }

  @Override
  public UserStatistics getUserStats(final int daysToCountAsActive) {
    Session session = getSessionFactory().getCurrentSession();
    String baseQuery = "from User u where u.username <> :anonUser";

    int total =
        session
            .createQuery("select count(u) " + baseQuery, Long.class)
            .setParameter("anonUser", RecordGroupSharing.ANONYMOUS_USER)
            .uniqueResult()
            .intValue();
    Long enabled =
        session
            .createQuery("select count(u) " + baseQuery + " and u.enabled = true", Long.class)
            .setParameter("anonUser", RecordGroupSharing.ANONYMOUS_USER)
            .uniqueResult();
    Long locked =
        session
            .createQuery("select count(u) " + baseQuery + " and u.accountLocked = true", Long.class)
            .setParameter("anonUser", RecordGroupSharing.ANONYMOUS_USER)
            .uniqueResult();

    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DAY_OF_YEAR, daysToCountAsActive * -1);
    Date from = cal.getTime();
    Long active =
        session
            .createQuery("select count(u) " + baseQuery + " and u.lastLogin > :from", Long.class)
            .setParameter("anonUser", RecordGroupSharing.ANONYMOUS_USER)
            .setParameter("from", from)
            .uniqueResult();

    UserStatistics stats =
        new UserStatistics(total, enabled.intValue(), locked.intValue(), active.intValue());
    Long enabledSysadmin = countUsersInRole(Role.SYSTEM_ROLE, session, true);
    Long enabledRSpaceAdmin = countUsersInRole(Role.ADMIN_ROLE, session, true);
    stats.setTotalEnabledRSpaceAdmins(enabledRSpaceAdmin.intValue());
    stats.setTotalEnabledSysAdmins(enabledSysadmin.intValue());
    return stats;
  }

  @Override
  public List<User> getViewableUsersByRole(User subject) {
    Session session = getSessionFactory().getCurrentSession();
    List<User> result;
    if (subject.hasRole(Role.ADMIN_ROLE)) {
      result = getCommunityUsers(subject, session, null);
      if (!result.contains(subject)) {
        result.add(subject);
      }

    } else if (subject.hasRole(Role.PI_ROLE)) {
      Query<User> q = session.createQuery(QUERY_GROUPMEMBER_STRING, User.class);
      q.setParameter("id", subject.getId());
      q.setParameter("roleInGroup", RoleInGroup.PI);
      q.setParameter("groupType", GroupType.LAB_GROUP);
      result = q.list();
      if (!result.contains(subject)) {
        result.add(subject);
      }
    } else {
      // we might be a lab admin RSPAC-333
      Query<User> q =
          session.createQuery(
              "select distinct ug2.user from UserGroup ug2 where ug2.group.id in"
                  + "( select group.id  from UserGroup ug where ug.user.id=:id "
                  + "and ug.roleInGroup=:roleInGroup and group.groupType=:groupType "
                  + "and ug.adminViewDocsEnabled=:enabled ) ",
              User.class);
      q.setParameter("id", subject.getId());
      q.setParameter("roleInGroup", RoleInGroup.RS_LAB_ADMIN);
      q.setParameter("groupType", GroupType.LAB_GROUP);
      q.setParameter(ENABLED, true);
      result = q.list();
      // if we aren't a labadmin, result set will be empty, so we just add subject to list.
      if (!result.contains(subject)) {
        result.add(subject);
      }
      // remove PIs
      result.removeIf(User::isPI);
    }
    return result;
  }

  public List<Long> getUserIdsInAdminsCommunity(User subject) {
    return getCommunityUsers(subject, getSession(), "id");
  }

  @SuppressWarnings("unchecked")
  private <T> List<T> getCommunityUsers(User subject, Session session, String projectionProperty) {
    List<Community> comms =
        session
            .createQuery(
                "select distinct c from Community c join c.admins admin where admin.id=:adminId",
                Community.class)
            .setParameter("adminId", subject.getId())
            .list();
    if (comms.isEmpty()) {
      return new ArrayList<>();
    }
    Community comm = comms.get(0);

    String selectClause =
        StringUtils.isBlank(projectionProperty)
            ? "select distinct u"
            : "select distinct u." + projectionProperty;
    String hql =
        selectClause
            + " from User u join u.userGroups ug join ug.group g join g.communities comm "
            + "where comm.id = :commId";
    @SuppressWarnings("unchecked")
    Query<T> query = (Query<T>) session.createQuery(hql);
    query.setParameter("commId", comm.getId());
    return query.list();
  }

  @Override
  public List<User> getViewableSharedRecordOwners(User subject) {
    return getSession()
        .createQuery(
            "select distinct rgs.shared.owner from RecordGroupSharing rgs where "
                + "rgs.sharee.id=:id or rgs.sharee.id in ( select group.id "
                + "from UserGroup ug where ug.user.id=:id)",
            User.class)
        .setParameter("id", subject.getId())
        .list();
  }

  @Override
  public List<UserView> getAllUsersView() {
    return getSession()
        .createQuery(
            "select new com.researchspace.model.views.UserView (id, username, email,"
                + " concat(lastName, ' ', firstName)) from User user where user.username !="
                + " :username",
            UserView.class)
        .setParameter(USERNAME, RecordGroupSharing.ANONYMOUS_USER)
        .list();
  }

  @Override
  public UserView getUserViewByUsername(String username) {
    return getSession()
        .createQuery(
            "select new com.researchspace.model.views.UserView (id, username, email,"
                + " concat(lastName, ' ', firstName)) from User where username=:username",
            UserView.class)
        .setParameter(USERNAME, username)
        .uniqueResult();
  }

  @Override
  public List<UserRoleView> getUsersBasicInfoWithRoles() {
    List<UserRoleViewProjection> rawList =
        getSession()
            .createQuery(
                "select new com.researchspace.dao.hibernate.UserRoleViewProjection( u.id,"
                    + " u.username, u.firstName, u.lastName, u.email,u.affiliation, role.name) from"
                    + " User u join u.roles role where role.name != 'ROLE_ANONYMOUS'",
                UserRoleViewProjection.class)
            .list();

    Map<String, UserRoleView> uNameToViewMap = flattenRawUserList(rawList);
    return new ArrayList<>(uNameToViewMap.values());
  }

  private Map<String, UserRoleView> flattenRawUserList(List<UserRoleViewProjection> rawList) {
    // group raw rows by username, items in list will be duplicate apart from role name
    Map<String, List<UserRoleViewProjection>> rcMap =
        rawList.stream().collect(groupingBy(k -> k.getUsername()));
    Map<String, UserRoleView> uNameToViewMap = new TreeMap<>();
    for (Entry<String, List<UserRoleViewProjection>> entry : rcMap.entrySet()) {
      List<UserRoleViewProjection> value = entry.getValue();
      if (value.size() == 1) {
        uNameToViewMap.put(entry.getKey(), value.get(0).toUserRoleView());
      } else {
        List<String> roleStrings =
            value.stream().map(UserRoleViewProjection::getRole).collect(toList());
        UserRoleView view = value.get(0).toUserRoleView();
        view.setRoles(roleStrings);
        // map is from grouping, so can't have 0 values
        uNameToViewMap.put(entry.getKey(), view);
      }
    }

    return uNameToViewMap;
  }

  @Override
  public List<String> getAllUserTags() {
    Set<String> result = new TreeSet<>();
    List<String> allUsersTagJsonStrings =
        getSession().createQuery("select distinct tagsJsonString from User").list();

    if (CollectionUtils.isNotEmpty(allUsersTagJsonStrings)) {
      for (String userTagsJsonString : allUsersTagJsonStrings) {
        if (StringUtils.isNotEmpty(userTagsJsonString)) {
          result.addAll(JacksonUtil.fromJson(userTagsJsonString, List.class));
        }
      }
    }
    return new ArrayList<>(result);
  }
}
