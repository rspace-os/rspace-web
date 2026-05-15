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
import org.hibernate.MappingException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.persister.entity.EntityPersister;
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
   * Replaces Hibernate 5's {@code Session.saveOrUpdate()}, which was removed in Hibernate 6. {@code
   * saveOrUpdate()} kept the original Java instance managed for both new and detached entities.
   * {@code merge()} cannot do this — it returns a copy, which causes {@code EntityExistsException}
   * when the original is already referenced by a parent collection in the session. So we use {@code
   * persist()} for new/managed entities (same-instance semantics) and {@code merge()} only for
   * detached entities.
   *
   * <p>{@inheritDoc}
   */
  @SuppressWarnings("unchecked")
  public T save(T object) {
    Session session = getSession();
    Object id = getEntityId(object, session);
    if (id == null) {
      session.persist(object);
      return object;
    }
    if (session.contains(object)) {
      session.persist(object);
      return object;
    }
    return (T) session.merge(object);
  }

  /**
   * Returns the database identifier of {@code object}, or {@code null} if the entity is new.
   *
   * <p>Uses Hibernate's {@link EntityPersister} to extract the identifier, which works correctly
   * for both field-access and property-access entities. {@code PersistenceUnitUtil.getIdentifier()}
   * incorrectly returns {@code null} for property-access entities in Hibernate 6. If the model
   * entities are migrated to field-access ({@code @Id} on the field instead of the getter), this
   * method could be replaced with a simple {@code PersistenceUnitUtil.getIdentifier()} call.
   */
  private Object getEntityId(Object object, Session session) {
    SessionImplementor si = (SessionImplementor) session;
    try {
      EntityPersister persister = si.getEntityPersister(null, object);
      return persister.getIdentifier(object, si);
    } catch (MappingException e) {
      // Entity type not registered with this persistence unit
      return null;
    }
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
