package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.SubSampleDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.service.inventory.InventoryMoveHelper;
import java.util.List;
import liquibase.database.Database;
import org.hibernate.type.LongType;

/**
 * With RSINV-30 we introduce workbench as a default location for subsamples.
 *
 * <p>This liquibase update creates the workbench for users who have inventory subsamples that are
 * outside of containers. Next, these subsamples are moved into the workbench.
 */
public class CreateWorkbenchAndMoveSubSamples_1_70 extends AbstractCustomLiquibaseUpdater {

  private ContainerDao containerDao;
  private SubSampleDao subSampleDao;
  private InventoryMoveHelper invMoveHelper;

  private int userProcessedCounter = 0;
  private int movedSubSamplesCounter = 0;

  @Override
  protected void addBeans() {
    containerDao = context.getBean(ContainerDao.class);
    subSampleDao = context.getBean(SubSampleDao.class);
    invMoveHelper = context.getBean(InventoryMoveHelper.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Moved " + movedSubSamplesCounter + " subsamples for " + userProcessedCounter + " users";
  }

  @Override
  protected void doExecute(Database database) {
    logger.info("executing subsamples move to workbench update");

    List<User> usersWithSamples = getUsersWithSamples();
    userProcessedCounter = usersWithSamples.size();
    logger.info("There are {} users owning inventory samples", userProcessedCounter);

    int i = 0;
    for (User user : usersWithSamples) {
      logger.info(
          "{} / {} - creating workbench and moving subsamples owned by {} ",
          i,
          userProcessedCounter,
          user.getUsername());
      try {
        Container wb = containerDao.getWorkbenchForUser(user);
        moveSubSamplesIntoWorkbench(wb, user);
        i++;

      } catch (Exception e) {
        logger.warn(
            "error when trying to create workbench/move subsamples for " + user.getUsername(), e);
      }
    }

    logger.info(
        "created and moved  "
            + movedSubSamplesCounter
            + " subsamples for "
            + i
            + " identified users");
    logger.info("workbench creation and subsamples move finished fine");
  }

  private List<User> getUsersWithSamples() {

    @SuppressWarnings("unchecked")
    List<Long> userIds =
        sessionFactory
            .getCurrentSession()
            .createSQLQuery(
                "select distinct s.owner_id as ownerId from SubSample ss join Sample s on"
                    + " ss.sample_id = s.id  where s.template = false and ss.parentLocation_id is"
                    + " null")
            .addScalar("ownerId", LongType.INSTANCE)
            .list();

    List<User> users =
        sessionFactory
            .getCurrentSession()
            .createQuery("from User where id in :userIds", User.class)
            .setParameterList("userIds", userIds)
            .list();

    return users;
  }

  private void moveSubSamplesIntoWorkbench(Container workbench, User user) {

    List<SubSample> subSamples =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from SubSample where sample.owner=:owner and sample.template = false and"
                    + " parentLocation is null",
                SubSample.class)
            .setParameter("owner", user)
            .list();

    ApiContainerInfo apiWorkbench = new ApiContainerInfo(workbench);
    for (SubSample ss : subSamples) {
      boolean moveSuccessful =
          invMoveHelper.moveRecordToTargetParentAndLocation(ss, apiWorkbench, null, user);
      if (moveSuccessful) {
        subSampleDao.save(ss);
        movedSubSamplesCounter++;
      }
    }
  }
}
