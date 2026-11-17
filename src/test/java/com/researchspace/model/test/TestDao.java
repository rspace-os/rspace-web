package com.researchspace.model.test;

import com.researchspace.model.AbstractUserOrGroupImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

public class TestDao {

  public <T> TestDao(SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  private SessionFactory sessionFactory;
  private Session batchSession;
  private final List<Object[]> batchMerges = new ArrayList<>();

  <T> T load(Long id, Class<T> clazz) {
    return load(id, clazz, null);
  }

  <T> T load(Long id, Class<T> clazz, Consumer<T> initializer) {
    Transaction transaction = null;
    T rc = null;
    Session session = null;
    try {
      if (batchSession != null) {
        rc = batchSession.get(clazz, id);
        if (initializer != null) {
          initializer.accept(rc);
        }
        return rc;
      }
      session = sessionFactory.openSession();
      // start a transaction
      transaction = session.beginTransaction();
      rc = session.get(clazz, id);

      if (initializer != null) {
        initializer.accept(rc);
      }

      // commit transaction
      transaction.commit();
      return rc;

    } catch (Exception e) {
      handleRollback(transaction, e);
    } finally {
      if (session != null) {
        session.close();
      }
    }
    return null;
  }

  <T> Long count(String tablename) {
    Transaction transaction = null;
    Long rc = 0L;
    Session session = null;
    try {
      session = sessionFactory.openSession();
      // start a transaction
      transaction = session.beginTransaction();
      // save the student object
      rc = session.createQuery("select count(id) from " + tablename, Long.class).getSingleResult();
      // commit transaction
      transaction.commit();
      return rc;

    } catch (Exception e) {
      handleRollback(transaction, e);
    } finally {
      session.close();
    }
    return null;
  }

  <T> T save(T item, Class<? extends T> clazz) {
    saveTogether(item);
    return item;
  }

  <T> T update(T item, Class<? extends T> clazz) {
    saveTogether(item);
    return item;
  }

  /**
   * Runs the saves in {@code work} in one session and transaction. Needed when a new object refers
   * to objects saved earlier in the same test: in separate sessions those would be detached, and
   * persist does not cascade onto detached objects.
   */
  void inOneTransaction(Runnable work) {
    Transaction transaction = null;
    try {
      batchSession = sessionFactory.openSession();
      transaction = batchSession.beginTransaction();
      work.run();
      transaction.commit();
      copyVersionsBack(batchMerges);
    } catch (RuntimeException e) {
      handleRollback(transaction, e);
      throw e;
    } finally {
      batchMerges.clear();
      if (batchSession != null) {
        batchSession.close();
        batchSession = null;
      }
    }
  }

  /**
   * Saves the items in the given order: new items are persisted, items with an id are merged. The
   * caller keeps its own objects; ids come back through persist and the merge listener, and a
   * user's new version is copied back here.
   */
  void saveTogether(Object... items) {
    if (batchSession != null) {
      saveInSession(batchSession, batchMerges, items);
      batchSession.flush();
      return;
    }
    Transaction transaction = null;
    Session session = null;
    List<Object[]> merges = new ArrayList<>();
    try {
      session = sessionFactory.openSession();
      transaction = session.beginTransaction();
      saveInSession(session, merges, items);
      transaction.commit();
      copyVersionsBack(merges);
    } catch (Exception e) {
      handleRollback(transaction, e);
      throw e;
    } finally {
      if (session != null) {
        session.close();
      }
    }
  }

  private void saveInSession(Session session, List<Object[]> merges, Object... items) {
    for (Object item : items) {
      if (session.contains(item)) {
        continue;
      }
      if (sessionFactory.getPersistenceUnitUtil().getIdentifier(item) == null) {
        session.persist(item);
      } else {
        merges.add(new Object[] {item, session.merge(item)});
      }
    }
  }

  private static void copyVersionsBack(List<Object[]> merges) {
    for (Object[] pair : merges) {
      if (pair[0] instanceof AbstractUserOrGroupImpl caller && pair[1] != caller) {
        caller.setVersion(((AbstractUserOrGroupImpl) pair[1]).getVersion());
      }
    }
  }

  <T> List<T> getAll(Class<T> clazz, String tableName) {
    Transaction transaction = null;
    Session session = null;
    List<T> rc = null;
    try {
      session = sessionFactory.openSession();
      // start a transaction
      transaction = session.beginTransaction();
      // save the student object
      rc = session.createQuery("from " + tableName, clazz).list();
      // commit transaction
      transaction.commit();
      return rc;

    } catch (Exception e) {
      handleRollback(transaction, e);
    } finally {
      session.close();
    }
    return null;
  }

  private void handleRollback(Transaction transaction, Exception e) {
    if (transaction != null) {
      transaction.rollback();
    }
    e.printStackTrace();
  }
}
