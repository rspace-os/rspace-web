package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RSForm;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** Regression tests for rejecting unsafe order-by values before query construction. */
public class OrderByInjectionGuardTest {

  // passed the legacy character blacklist, but is an ORDER BY injection payload
  private static final String PAYLOAD = "name,rand()";

  /**
   * setOrderBy drops unsafe values, so building a criteria object that still carries one simulates
   * a value that reached the DAO by another route and pins the DAO-side guard.
   */
  @SuppressWarnings("unchecked")
  private <T> PaginationCriteria<T> unsafeCriteria() {
    PaginationCriteria<T> pgCrit = mock(PaginationCriteria.class);
    when(pgCrit.getOrderBy()).thenReturn(PAYLOAD);
    when(pgCrit.isOrderBySafe(PAYLOAD)).thenReturn(false);
    when(pgCrit.getSortOrder()).thenReturn(SortOrder.ASC);
    return pgCrit;
  }

  @Test
  public void paginationCriteriaDropsPayloadOnSet() {
    PaginationCriteria<BaseRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(BaseRecord.class);
    pgCrit.setOrderBy(PAYLOAD);
    assertFalse(PAYLOAD.equals(pgCrit.getOrderBy()));
  }

  @Test
  public void formDaoMakeOrderByFallsBackToStableSort() {
    FormDaoHibernate formDao = new FormDaoHibernate();
    PaginationCriteria<RSForm> safe = PaginationCriteria.createDefaultForClass(RSForm.class);
    safe.setSortOrder(SortOrder.ASC);
    safe.setOrderBy("name");
    assertTrue(formDao.makeOrderBy(safe).contains("order by name"));

    String out = formDao.makeOrderBy(unsafeCriteria());
    assertFalse(out.contains("rand()"));
    assertFalse(out.contains(","));
    assertTrue(out.contains("order by id")); // stable fallback so paging stays deterministic

    assertEquals("", formDao.makeOrderBy(null));
  }

  @Test
  public void auditDaoMakeOrderByFallsBackToDeletedDate() {
    PaginationCriteria<AuditedRecord> safe =
        PaginationCriteria.createDefaultForClass(AuditedRecord.class);
    safe.setSortOrder(SortOrder.ASC);
    safe.setOrderBy("name");
    // logical sort names are mapped to fully-qualified HQL paths
    assertTrue(
        AuditDaoHibernateEnversImpl.makeOrderBy(safe)
            .contains("order by rtf.record.editInfo.name"));

    String out = AuditDaoHibernateEnversImpl.makeOrderBy(unsafeCriteria());
    assertFalse(out.contains("rand()"));
    assertFalse(out.contains(","));
    // this site has its own default sort, so it stays silent rather than logging
    assertTrue(out.contains("order by rtf.deletedDate"));
  }

  @Test
  public void recordDaoMakeOrderByStripsInjection() {
    PaginationCriteria<BaseRecord> safe =
        PaginationCriteria.createDefaultForClass(BaseRecord.class);
    safe.setSortOrder(SortOrder.ASC);
    safe.setOrderBy("name");
    assertTrue(RecordDaoHibernate.makeOrderBy(safe).contains("order by br.editInfo.name"));

    String out = RecordDaoHibernate.makeOrderBy(unsafeCriteria());
    assertFalse(out.contains("rand()"));
    assertFalse(out.contains(","));
  }

  @Test
  public void userDaoSafeOrderByStripsInjection() throws Exception {
    UserDaoHibernate dao = new UserDaoHibernate();
    Method m = UserDaoHibernate.class.getDeclaredMethod("safeOrderBy", PaginationCriteria.class);
    m.setAccessible(true);

    PaginationCriteria<User> safe = PaginationCriteria.createDefaultForClass(User.class);
    safe.setSortOrder(SortOrder.ASC);
    safe.setOrderBy("lastName");
    assertTrue(((String) m.invoke(dao, safe)).contains("order by u.lastName"));

    String out = (String) m.invoke(dao, unsafeCriteria());
    assertFalse(out.contains("rand")); // payload not used
    assertTrue(out.contains("u.id")); // stable fallback so paging stays deterministic
  }

  // UserDao applies custom tie-break ordering instead of the shared guard.
  @Test
  @SuppressWarnings("unchecked")
  public void userDaoApplyUserOrderIgnoresInjection() throws Exception {
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

    // an unsafe value must not reach the query; a stable fallback is applied instead
    m.invoke(dao, unsafeCriteria(), builder, root, query);
    verify(root).get("id");
    verify(root, never()).get(PAYLOAD);
    verify(query).orderBy(org.mockito.ArgumentMatchers.any(Order.class));
  }
}
