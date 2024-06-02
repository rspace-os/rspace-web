package com.researchspace.dao.customliquibaseupdates;

import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.apache.commons.lang3.SystemUtils.OS_NAME;

import liquibase.database.Database;
import liquibase.exception.CustomPreconditionErrorException;
import liquibase.exception.CustomPreconditionFailedException;
import liquibase.precondition.CustomPrecondition;

/** A custom liquibase precondition filter that passes if the OS <strong>is</strong> windows */
public class IsWindowsPrecondition implements CustomPrecondition {

  @Override
  public void check(Database database)
      throws CustomPreconditionFailedException, CustomPreconditionErrorException {
    if (!IS_OS_WINDOWS) {
      throw new CustomPreconditionFailedException("OS is not Windows, it's: " + OS_NAME);
    }
  }
}
