package com.researchspace.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * Generic DAO (Data Access Object) with common methods to CRUD POJOs.
 *
 * @param <T> a type variable
 * @param <PK> the primary key for that type
 */
public interface GenericDao<T, PK extends Serializable> {

  /**
   * Generic method used to get all objects of a particular type. This is the same as lookup up all
   * rows in a table.
   *
   * @return List of populated objects
   */
  List<T> getAll();

  /**
   * Gets all records without duplicates.
   *
   * <p>Note that if you use this method, it is imperative that your model classes correctly
   * implement the hashcode/equals methods
   *
   * @return List of populated objects
   */
  List<T> getAllDistinct();

  /**
   * Generic method to get an object based on class and identifier. An
   * ObjectRetrievalFailureException Runtime Exception is thrown if nothing is found.<br>
   * Use this method when the object is expected to be found.
   *
   * @param id the identifier (primary key) of the object to get
   * @return a populated object
   * @see org.springframework.orm.ObjectRetrievalFailureException
   */
  T get(PK id);

  /**
   * Reads an entity and holds a row lock on it ({@code SELECT ... FOR UPDATE}) until the current
   * transaction ends, so a concurrent transaction reading the same row this way waits for this one
   * to commit. Use it where two requests writing one row must serialise rather than both compute
   * from the same stale read (RSDEV-1231).
   *
   * <p>The row is re-read under the lock, so the returned entity holds what another transaction
   * committed rather than what this one saw beforehand. The caller must therefore not hold
   * unflushed changes to a row it locks here for the first time: they would be discarded. A row
   * this transaction already holds exclusively is exempt and is returned as it is, so asking twice
   * is a no-op and a caller's own pending changes are safe.
   *
   * @return the locked entity, or null if none has the id
   */
  T getForUpdate(PK id);

  /**
   * Alternative object retriever, which just returns <code>null</code> if an item is not found,
   * rather than throwing an exception. Use this method when the id may not exist ( for example, if
   * from the identifier it is not clear what table to use (e.g., record or folder id; snippet or
   * field id lookups).
   *
   * @param id the identifier (primary key) of the object to get
   * @return An Optional<T> to force handling of situation when object retrieved is <code>null
   *     </code>.
   */
  Optional<T> getSafeNull(PK id);

  /**
   * Checks for existence of an object of type T using the id arg.
   *
   * @param id the id of the entity
   * @return - true if it exists, false if it doesn't
   */
  boolean exists(PK id);

  /**
   * Generic method to save an object - handles both update and insert.
   *
   * @param object the object to save
   * @return the persisted object
   */
  T save(T object);

  /**
   * Generic method to delete an object based on class and id
   *
   * @param id the identifier (primary key) of the object to remove
   */
  void remove(PK id);

  /**
   * Gets an Optional by simple natural ID ( a single attribute of type N, belonging to type T).
   *
   * @param naturalId
   * @return An Optional<T>.
   */
  <N> Optional<T> getBySimpleNaturalId(N naturalId);

  /**
   * Loads a proxied instance, not hitting the database, with only the id set using session.load()
   *
   * @param id
   * @return the persisted object
   */
  T load(PK id);

  Long getCount();

  /**
   * Generic Search interface for paginated and query listings. For queries, add a FilterCriteria
   * subclass in to PaginationCriteria. <br>
   * The default implementation returns an empty search result.
   *
   * @param pgCrit
   * @param searcher
   * @return ISearchResults<T>
   */
  default ISearchResults<T> search(PaginationCriteria<T> pgCrit, User searcher) {
    return SearchResultsImpl.emptyResult(pgCrit);
  }
}
