package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.linkedelements.FieldParser;
import com.researchspace.service.FieldManager;
import com.researchspace.service.FolderManager;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * Implements {@link CustomTaskChange} with some default implementations and sets up
 * Spring/Hibernate context. Subclasses can just extend and implement <code>doExecute()</code>.<br>
 * A {@link FieldParser} and {@link FieldManager} are supplied; get any other beans you need from
 * the supplied {@link ApplicationContext}.<br>
 * We can't autowire, as this class is instantiated from within Liquibase, so we need to acquire
 * beans manually.
 */
public abstract class AbstractCustomLiquibaseUpdater implements CustomTaskChange {

  protected Logger logger = LoggerFactory.getLogger(getClass());
  protected FieldParser fieldParser;
  protected FieldManager fMger;
  protected SessionFactory sessionFactory;
  protected ApplicationContext context;
  protected TransactionStatus status;

  /**
   * Sets application context and populates some beans in to this class:
   *
   * <ul>
   *   <li>{@link FolderManager}
   *   <li>{@link FieldParser}
   *   <li>{@link SessionFactory}
   * </ul>
   *
   * Subclasses should not override this method but extend <code> addBeans() </code>
   */
  @Override
  public final void setUp() throws SetupException {
    this.context = AppContextRetriever.getApplicationContext();
    this.fMger = context.getBean("fieldManager", FieldManager.class);
    this.fieldParser = context.getBean("fieldParser", FieldParser.class);
    this.sessionFactory = context.getBean("sessionFactory", SessionFactory.class);
    addBeans();
  }

  /**
   * Override this method to add more Spring beans as required for your update class.<br>
   * This method is a no-op implementation.
   */
  protected void addBeans() {}

  /*
   * Does nothing, override in subclass to add custom functionality
   * (non-Javadoc)
   *
   * @see
   * liquibase.change.custom.CustomChange#setFileOpener(liquibase.resource
   * .ResourceAccessor)
   */
  @Override
  public void setFileOpener(ResourceAccessor resourceAccessor) {}

  /*
   * Returns empty errors list; override in subclass if need be. (non-Javadoc)
   *
   * @see
   * liquibase.change.custom.CustomChange#validate(liquibase.database.Database
   * )
   */
  @Override
  public ValidationErrors validate(Database database) {
    return new ValidationErrors();
  }

  /**
   * Gets the Spring-managed transaction manager
   *
   * @return
   */
  protected PlatformTransactionManager getTxMger() {
    return context.getBean(PlatformTransactionManager.class);
  }

  public void openTransaction(PlatformTransactionManager txMgr) {
    status =
        txMgr.getTransaction(
            new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRED));
  }

  /** Commits a transaction */
  protected void commitTransaction() {
    getTxMger().commit(status);
  }

  /**
   * Default implementation opens a Spring/Hibernate transaction and closes at end. Uses template
   * pattern to help subclass writers
   */
  @Override
  public void execute(Database database) throws CustomChangeException {
    logger.info("Calling custom task - {}", getClass().getName());
    openTransaction(getTxMger());
    doExecute(database);
    commitTransaction();
    logger.info("{} committed", getClass().getName());
  }

  /**
   * Subclasses must implement this method to do the actual work. This method will be called from
   * within a transaction.
   *
   * @param database
   */
  protected abstract void doExecute(Database database);
}
