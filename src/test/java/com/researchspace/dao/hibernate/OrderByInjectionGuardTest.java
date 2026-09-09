package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.core.util.SortOrder;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.sort.FormSort;
import com.researchspace.model.sort.UnknownSortKeyException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * Each DAO resolves the requested sort key with its listing's sort enum before building a query, so
 * a value that is not a known key is rejected and never reaches query text.
 */
public class OrderByInjectionGuardTest {

  // passed the legacy character blacklist, but is an ORDER BY injection payload
  private static final String PAYLOAD = "name,rand()";

  private <T> PaginationCriteria<T> criteriaWith(Class<T> clazz, String orderBy) {
    PaginationCriteria<T> pgCrit = PaginationCriteria.createDefaultForClass(clazz);
    pgCrit.setSortOrder(SortOrder.ASC);
    pgCrit.setOrderBy(orderBy);
    return pgCrit;
  }

  @Test
  public void formDaoSelectsAndOrdersByTheResolvedColumnOnly() {
    assertEquals(", form.name ", FormDaoHibernate.sortSelectColumn(FormSort.NAME));
    assertEquals(", owner.username ", FormDaoHibernate.sortSelectColumn(FormSort.OWNER));
    assertEquals("", FormDaoHibernate.sortSelectColumn(FormSort.ID));
    assertEquals(" order by name ASC", FormDaoHibernate.makeOrderBy(FormSort.NAME, SortOrder.ASC));
    assertEquals(" order by id ", FormDaoHibernate.makeOrderBy(FormSort.ID, null));
    assertThrows(UnknownSortKeyException.class, () -> FormSort.fromRequest(PAYLOAD));
  }

  @Test
  public void auditDaoMapsKeysToQualifiedPathsAndRejectsUnknownKeys() {
    assertTrue(
        AuditDaoHibernateEnversImpl.makeOrderBy(criteriaWith(AuditedRecord.class, "name"))
            .contains("order by rtf.record.editInfo.name"));
    assertTrue(
        AuditDaoHibernateEnversImpl.makeOrderBy(criteriaWith(AuditedRecord.class, null))
            .contains("order by rtf.deletedDate"));
    assertThrows(
        UnknownSortKeyException.class,
        () -> AuditDaoHibernateEnversImpl.makeOrderBy(criteriaWith(AuditedRecord.class, PAYLOAD)));
  }

  @Test
  public void userDaoSafeOrderByRejectsUnknownKeys() throws Exception {
    UserDaoHibernate dao = new UserDaoHibernate();
    Method m = UserDaoHibernate.class.getDeclaredMethod("safeOrderBy", PaginationCriteria.class);
    m.setAccessible(true);

    assertTrue(
        ((String) m.invoke(dao, criteriaWith(User.class, "lastName")))
            .contains("order by u.lastName"));
    // only mapped attributes are orderable, so a real column that is not a sort key is refused too
    assertUnknownSortKey(() -> m.invoke(dao, criteriaWith(User.class, "password")));
    assertUnknownSortKey(() -> m.invoke(dao, criteriaWith(User.class, PAYLOAD)));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void userDaoApplyUserOrderRejectsUnknownKeysBeforeTouchingTheQuery() throws Exception {
    UserDaoHibernate dao = new UserDaoHibernate();
    Method m =
        UserDaoHibernate.class.getDeclaredMethod(
            "applyUserOrder",
            PaginationCriteria.class,
            CriteriaBuilder.class,
            Root.class,
            CriteriaQuery.class);
    m.setAccessible(true);

    CriteriaBuilder builder = mock(CriteriaBuilder.class);
    Root<User> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    Path<Object> path = mock(Path.class);
    when(root.get(anyString())).thenReturn(path);
    when(builder.asc(path)).thenReturn(mock(Order.class));

    assertUnknownSortKey(
        () -> m.invoke(dao, criteriaWith(User.class, PAYLOAD), builder, root, query));
    verify(root, never()).get(anyString());
    verify(query, never()).orderBy(org.mockito.ArgumentMatchers.anyList());

    m.invoke(dao, criteriaWith(User.class, "username"), builder, root, query);
    verify(root).get("username");
  }

  private interface ReflectiveCall {
    Object call() throws Exception;
  }

  private static void assertUnknownSortKey(ReflectiveCall call) {
    InvocationTargetException wrapped = assertThrows(InvocationTargetException.class, call::call);
    assertTrue(
        wrapped.getCause() instanceof UnknownSortKeyException,
        "expected UnknownSortKeyException but was " + wrapped.getCause());
  }
}
