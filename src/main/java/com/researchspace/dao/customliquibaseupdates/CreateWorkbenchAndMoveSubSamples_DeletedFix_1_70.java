package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.dao.SubSampleDao;
import com.researchspace.model.inventory.SubSample;
import java.util.List;
import liquibase.database.Database;

/**
 * With #{@link CreateWorkbenchAndMoveSubSamples_1_70} update the subsamples that had no parent
 * location were moved to owner's workbench. That inadvertently moved deleted subsamples to the
 * workbench.
 *
 * <p>This update removes deleted subsamples from the parent container.
 */
public class CreateWorkbenchAndMoveSubSamples_DeletedFix_1_70
    extends AbstractCustomLiquibaseUpdater {

  private SubSampleDao subSampleDao;

  private int movedSubSamplesCounter = 0;

  @Override
  protected void addBeans() {
    subSampleDao = context.getBean(SubSampleDao.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Moved " + movedSubSamplesCounter + " deleted subsamples out of their current location";
  }

  @Override
  protected void doExecute(Database database) {
    logger.info("executing deleted subsamples move update");

    @SuppressWarnings("unchecked")
    List<SubSample> subSamples =
        sessionFactory
            .getCurrentSession()
            .createQuery("from SubSample where deleted = true and parentLocation_id is not null")
            .list();

    for (SubSample ss : subSamples) {
      ss.removeFromCurrentParent();
      subSampleDao.save(ss);
      movedSubSamplesCounter++;
    }

    logger.info(
        "moved " + movedSubSamplesCounter + " deleted subsamples out of their current location");
    logger.info("deleted subsamples move finished fine");
  }
}
