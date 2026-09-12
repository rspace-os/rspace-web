package com.researchspace.dao.resourceaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import java.util.List;
import java.util.Set;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

class ResourceAccessDirectoryDaoHibernateTest {
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
