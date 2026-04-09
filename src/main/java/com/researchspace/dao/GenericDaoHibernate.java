package com.researchspace.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.model.PaginationCriteria;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;

/**
 * This class serves as the Base class for all other DAOs - namely to hold common CRUD methods that
 * they might all use. You should only need to extend this class when your require custom CRUD
 * logic.
 *
 * <p>
 *
 * <p>
 *
 * @param <T> a type variable
 * @param <PK> the primary key for that type
 */
public class GenericDaoHibernate<T, PK extends Serializable> implements GenericDao<T, PK> {

  /** Logger variable for all child classes. Uses slf4j */
  protected final Logger log = LoggerFactory.getLogger(getClass());

  protected Class<T> persistentClass;

  protected SessionFactory sessionFactory;

  /**
   * Constructor that takes in a class to see which type of entity to persist. Use this constructor
   * when subclassing.
   *
   * @param persistentClass the class type you'd like to persist
   */
  public GenericDaoHibernate(final Class<T> persistentClass) {
    this.persistentClass = persistentClass;
  }

  /**
   * Constructor that takes in a class and sessionFactory for easy creation of DAO.
   *
   * @param persistentClass the class type you'd like to persist
   * @param sessionFactory the pre-configured Hibernate SessionFactory
   */
  public GenericDaoHibernate(final Class<T> persistentClass, SessionFactory sessionFactory) {
    this.persistentClass = persistentClass;
    this.sessionFactory = sessionFactory;
  }

  public SessionFactory getSessionFactory() {
    return this.sessionFactory;
  }

  /**
   * Gets the current session for use in queries
   *
   * @return
   */
  protected Session getSession() {
    return sessionFactory.getCurrentSession();
  }

  @Autowired
  public void setSessionFactory(SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  /** {@inheritDoc} */
  @SuppressWarnings("unchecked")
  public List<T> getAll() {
    String hql = "select distinct e from " + persistentClass.getName() + " e";
    return getSession().createQuery(hql, persistentClass).list();
  }

  public Long getCount() {
    String hql = "select count(e.id) from " + persistentClass.getName() + " e";
    return getSession().createQuery(hql, Long.class).uniqueResult();
  }

  /** {@inheritDoc} */
  public List<T> getAllDistinct() {
    Collection<T> result = new LinkedHashSet<T>(getAll());
    return new ArrayList<T>(result);
  }

  /** {@inheritDoc} */
  public T get(PK id) {
    Optional<T> entity = getSafeNull(id);
    return entity.orElseThrow(
        () -> {
          log.warn("Uh oh, '{}' object with id '{}' not found...", this.persistentClass, id);
          return new ObjectRetrievalFailureException(this.persistentClass, id);
        });
  }

  /** {@inheritDoc} */
  public boolean exists(PK id) {
    T entity = (T) getSession().get(this.persistentClass, id);
    return entity != null;
  }

  /**
   * Saves or updates the entity, mirroring the semantics of the removed {@code
   * Session.saveOrUpdate()}.
   *
   * <ul>
   *   <li><b>New entities (id == null)</b>: {@link Session#persist} is used so that the
   *       <em>same</em> Java instance becomes managed. This is important when the entity is already
   *       present in a parent collection — a {@code merge()} copy would create two managed
   *       instances for the same row and cause {@link jakarta.persistence.EntityExistsException} on
   *       the next cascade.
   *   <li><b>Entities with an id</b>: {@link Session#merge} is used. This correctly handles both
   *       detached entities and already-managed entities (including property-access entities where
   *       {@link jakarta.persistence.PersistenceUnitUtil#getIdentifier} incorrectly returns {@code
   *       null} in Hibernate 6). Merge also cascades to {@code CascadeType.MERGE} child
   *       associations, ensuring that new children added after the initial persist (e.g. {@code
   *       StoichiometryMolecule} added to an already-persisted {@code Stoichiometry}) are
   *       themselves inserted and assigned their database ids.
   * </ul>
   *
   * <p>For new entities, the database-generated id is set on the entity immediately by the {@code
   * IDENTITY} strategy. For entities that reach the {@code merge()} path while transient, {@link
   * com.researchspace.dao.spring.ext.IdTransferringMergeEventListener} copies the generated id back
   * to the original instance.
   *
   * <p>{@inheritDoc}
   */
  @SuppressWarnings("unchecked")
  public T save(T object) {
    Session session = getSession();
    Object id = getEntityId(object, session);
    if (id == null) {
      // New entity: persist() keeps the same Java instance managed (no copy created).
      session.persist(object);
      return object;
    }
    if (session.contains(object)) {
      // For managed entities, persist() is a no-op on the entity itself but the JPA spec
      // guarantees it cascades CascadeType.PERSIST/ALL to any new transient children reachable
      // through cascade relationships (e.g. newly-added ExtraField, DigitalObjectIdentifier).
      // This is intentionally preferred over merge() here: merge() on a managed entity in
      // Hibernate 6 re-initialises lazy collection proxies from the database, which overwrites
      // in-memory soft-deletion changes (e.g. doi.setDeleted(true)) before they can be flushed.
      session.persist(object);
      return object;
    }
    // Detached entity: merge() re-attaches it and cascades to new transient children.
    return (T) session.merge(object);
  }

  /**
   * Returns the database identifier of {@code object}, or {@code null} if the entity is new.
   *
   * <p>Tries {@link jakarta.persistence.PersistenceUnitUtil#getIdentifier} first (works for
   * field-access entities). Falls back to reflective invocation of {@code getId()} for
   * property-access entities where Hibernate 6's {@code PersistenceUnitUtil} incorrectly returns
   * {@code null} even when the entity has a non-null id.
   */
  private Object getEntityId(Object object, Session session) {
    try {
      Object id = session.getSessionFactory().getPersistenceUnitUtil().getIdentifier(object);
      if (id != null) {
        return id;
      }
    } catch (IllegalArgumentException e) {
      // Entity type not registered with this persistence unit
    }
    // Fallback for property-access entities (e.g. BaseRecord, RecordToFolder, UserAppConfig).
    Class<?> cls = object.getClass();
    while (cls != null) {
      try {
        java.lang.reflect.Method m = cls.getDeclaredMethod("getId");
        m.setAccessible(true);
        return m.invoke(object);
      } catch (NoSuchMethodException e) {
        cls = cls.getSuperclass();
      } catch (ReflectiveOperationException e) {
        break;
      }
    }
    return null;
  }

  /** {@inheritDoc} */
  public void remove(PK id) {
    getSession().delete(this.get(id));
  }

  /**
   * Convenience method to return an empty {@link ISearchResults} object
   *
   * @param pgCrit
   * @return
   */
  protected ISearchResults<T> createEmptyResultSet(PaginationCriteria<T> pgCrit) {
    return SearchResultsImpl.emptyResult(pgCrit);
  }

  @Override
  public Optional<T> getSafeNull(PK id) {
    T rc = (T) getSession().get(this.persistentClass, id);
    return Optional.ofNullable(rc);
  }

  @Override
  public <N> Optional<T> getBySimpleNaturalId(N naturalId) {
    T value = getSession().bySimpleNaturalId(this.persistentClass).load(naturalId);
    return Optional.ofNullable(value);
  }

  @Override
  public T load(PK id) {
    return (T) getSession().load(this.persistentClass, id);
  }

  /**
   * Utility to get first result from a list where a single unique result is expected, but there is
   * not a DB unique constraint, so calling uniqueResults might produce an error.
   *
   * @param q A typed query
   * @param clazz
   * @return The 1st result from a list or <code>null</code>
   */
  protected T getFirstResultOrNull(Query<T> q) {
    List<T> results = q.list();
    T rc = null;
    // should be the case
    if (results.size() == 1) {
      rc = results.get(0);
      // probably something wrong
    } else if (results.size() > 1) {
      log.warn("Non-unique result for query: {}", q.getQueryString());
      rc = results.get(0);
    }
    return rc;
  }
}
