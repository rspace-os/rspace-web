package com.researchspace.dao.resourceaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import java.util.List;
import java.util.Set;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ResourceAccessDirectoryDaoHibernateTest {
  @Test
  void searchesDisplayedFullNamesWithoutRemovingScopeOrLiteralWildcardEscaping() {
    SessionFactory factory = mock(SessionFactory.class);
    Session session = mock(Session.class);
    Query<User> users = mock(Query.class, RETURNS_SELF);
    Query<Group> groups = mock(Query.class, RETURNS_SELF);
    User subject = mock(User.class);
    when(subject.getId()).thenReturn(42L);
    when(factory.getCurrentSession()).thenReturn(session);
    when(session.createQuery(anyString(), eq(User.class))).thenReturn(users);
    when(session.createQuery(anyString(), eq(Group.class))).thenReturn(groups);
    when(users.getResultList()).thenReturn(List.of());
    when(groups.getResultList()).thenReturn(List.of());

    new ResourceAccessDirectoryDaoHibernate(factory).search("User First%_", 20, subject);

    ArgumentCaptor<String> hql = ArgumentCaptor.forClass(String.class);
    verify(session).createQuery(hql.capture(), eq(User.class));
    assertTrue(
        hql.getValue().contains("lower(concat(user.firstName, ' ', user.lastName)) like :query"));
    assertTrue(hql.getValue().contains("ownMembership.user.id = :subjectId"));
    verify(users).setParameter("query", "%user first\\%\\_%");
    verify(users).setParameter("subjectId", 42L);
    verify(users).setMaxResults(20);
  }

  @Test
  void resolvesSeededNegativeUserIdsThroughTheAssignableDirectoryQuery() {
    SessionFactory factory = mock(SessionFactory.class);
    Session session = mock(Session.class);
    Query<User> query = mock(Query.class);
    User subject = mock(User.class);
    User seededUser = new User();
    seededUser.setId(-3L);
    seededUser.setUsername("seeded");
    when(subject.isEnabled()).thenReturn(true);
    when(subject.hasSysadminRole()).thenReturn(true);
    when(subject.getId()).thenReturn(-12L);
    when(factory.getCurrentSession()).thenReturn(session);
    when(session.createQuery(anyString(), eq(User.class))).thenReturn(query);
    when(query.getResultList()).thenReturn(List.of(seededUser));

    var result =
        new ResourceAccessDirectoryDaoHibernate(factory)
            .resolveAssignable(Set.of("user:-3", "user:0", "user:invalid"), subject);

    assertEquals(Set.of("user:-3"), result.keySet());
    verify(query).setParameter("ids", Set.of(-3L));
  }
}
