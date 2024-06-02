package com.researchspace.dao;

/** General wrapper for Hibernate/DB utility code to hide implementation from client code. */
public interface DAOUtils {

  /**
   * Initialises an entity within a session
   *
   * @param entity
   * @return the initialised object
   */
  public <T> T initializeAndUnproxy(T entity);
}
