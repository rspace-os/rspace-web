package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.core.util.CryptoUtils;
import com.researchspace.dao.UserApiKeyDao;
import com.researchspace.model.UserApiKey;
import java.util.List;
import liquibase.database.Database;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CryptingApiKeysAndUpdateDatabase_RSDEV310 extends AbstractCustomLiquibaseUpdater {

  private UserApiKeyDao userApiKeyDao;
  private int recordsUpdated = 0;

  @Override
  protected void addBeans() {
    userApiKeyDao = context.getBean(UserApiKeyDao.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Updated " + recordsUpdated + " records";
  }

  @Override
  protected void doExecute(Database database) {

    List<UserApiKey> userApiKeyListToCheck = userApiKeyDao.getAll();

    userApiKeyListToCheck.stream()
        .filter(userKey -> userKey.getApiKey().length() <= 32)
        .forEach(
            userKeyToUpdate -> {
              userKeyToUpdate.setApiKey(CryptoUtils.encodeBCrypt(userKeyToUpdate.getApiKey()));
              userApiKeyDao.save(userKeyToUpdate);
              recordsUpdated++;
            });
  }
}
