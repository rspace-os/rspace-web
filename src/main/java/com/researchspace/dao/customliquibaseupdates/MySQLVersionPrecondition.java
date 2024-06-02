package com.researchspace.dao.customliquibaseupdates;

import static java.lang.String.format;

import com.researchspace.core.util.version.SemanticVersion;
import liquibase.database.Database;
import liquibase.exception.CustomPreconditionErrorException;
import liquibase.exception.CustomPreconditionFailedException;
import liquibase.exception.DatabaseException;
import liquibase.precondition.CustomPrecondition;
import org.apache.commons.lang.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A custom liquibase precondition filter for comparing a desired DB version with real DB version.
 *
 * <p>Takes 2 parameters - 'version' - the desired version of the DB and 'operator' - 'gte' , 'lt',
 * or 'eq' which detemines whether the actual DB version should be gt or equal, less than, or equal
 * to the specified version.
 *
 * <p>Only major and minor db versions are considered.
 */
public class MySQLVersionPrecondition implements CustomPrecondition {

  Logger log = LoggerFactory.getLogger(MySQLVersionPrecondition.class);

  public MySQLVersionPrecondition() {
    super();
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  private String version;
  private String operator;

  public String getOperator() {
    return operator;
  }

  /**
   * @param operator 'eq', 'gte' or 'lt'
   */
  public void setOperator(String operator) {
    Validate.isTrue(isValidOperator(operator));
    this.operator = operator;
  }

  private boolean isValidOperator(String operator) {
    return "gte".equals(operator) || "lt".equals(operator) || "eq".equals(operator);
  }

  @Override
  public void check(Database database)
      throws CustomPreconditionFailedException, CustomPreconditionErrorException {
    SemanticVersion testSQLVersion = new SemanticVersion(version);

    try {
      int minor = database.getConnection().getDatabaseMinorVersion();
      int major = database.getConnection().getDatabaseMajorVersion();
      log.info(
          "Testing if DB with major: {}, minor: {} is {} than {}",
          major,
          minor,
          operator,
          testSQLVersion);
      SemanticVersion actualVersion = new SemanticVersion(major, minor, null, null);
      if ("gte".equals(operator)) {
        if (!actualVersion.isSameOrNewerThan(testSQLVersion)) {
          String message =
              format("Expected DB version gte %s but was %s", testSQLVersion, actualVersion);
          throw new CustomPreconditionFailedException(message);
        }
      } else if ("lt".equals(operator)) {
        if (!actualVersion.isOlderThan(testSQLVersion)) {
          String message =
              format(
                  "Expected DB version older than  %s but was %s", testSQLVersion, actualVersion);
          throw new CustomPreconditionFailedException(message);
        }
      } else if ("eq".equals(operator)) {
        if (!actualVersion.equals(testSQLVersion)) {
          String message =
              format("Expected DB version equal to   %s but was %s", testSQLVersion, actualVersion);
          throw new CustomPreconditionFailedException(message);
        }
      } else {
        throw new CustomPreconditionErrorException(
            format("Unknown operator [%s] - must be 'gte' or 'lt'", operator));
      }
    } catch (DatabaseException e) {
      throw new CustomPreconditionErrorException("Could not retrieve DB version", e);
    }
  }

  MySQLVersionPrecondition(String version, String operator) {
    super();
    this.version = version;
    this.operator = operator;
  }
}
