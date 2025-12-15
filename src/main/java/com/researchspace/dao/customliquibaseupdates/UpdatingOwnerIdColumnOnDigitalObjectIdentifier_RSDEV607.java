package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.dao.DigitalObjectIdentifierDao;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import java.util.List;
import liquibase.database.Database;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdatingOwnerIdColumnOnDigitalObjectIdentifier_RSDEV607
    extends AbstractCustomLiquibaseUpdater {

  private DigitalObjectIdentifierDao digitalObjectIdentifierDao;
  private int recordsUpdated = 0;

  @Override
  protected void addBeans() {
    digitalObjectIdentifierDao = context.getBean(DigitalObjectIdentifierDao.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Updated " + recordsUpdated + " records";
  }

  @Override
  protected void doExecute(Database database) {

    List<DigitalObjectIdentifier> doiListToUpdate = digitalObjectIdentifierDao.getAll();
    for (DigitalObjectIdentifier currentDoi : doiListToUpdate) {
      currentDoi.setOwner(currentDoi.getInventoryRecord().getOwner());
      digitalObjectIdentifierDao.save(currentDoi);
      recordsUpdated++;
    }
  }
}
