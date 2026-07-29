package com.researchspace.dao.customliquibaseupdates;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.core.util.version.SemanticVersion;
import liquibase.database.Database;
import liquibase.database.DatabaseConnection;
import liquibase.exception.CustomPreconditionErrorException;
import liquibase.exception.CustomPreconditionFailedException;
import liquibase.exception.DatabaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MySQLVersionPreconditionTest {

  @Mock Database db;
  @Mock DatabaseConnection conn;
  MySQLVersionPrecondition precondition;

  @BeforeEach
  public void setUp() throws Exception {
    precondition = new MySQLVersionPrecondition();
  }

  @Test
  public void invalidOperatorCausesError() throws DatabaseException {
    precondition.setVersion("5.7");
    assertThrows(IllegalArgumentException.class, () -> precondition.setOperator("notanoperator"));
  }

  @Test
  public void tooOldDBFailure() throws CustomPreconditionErrorException, DatabaseException {
    // want 5.7 but is 5.6
    setUpDBVersion(new SemanticVersion(5, 6, null, null));
    precondition.setVersion("5.7");
    precondition.setOperator("gte");
    assertThrows(
        CustomPreconditionFailedException.class,
        () -> // fails - should ne > =5.7 but is 5.6
        precondition.check(db));
  }

  @Test
  public void precondition_GTE_Pass()
      throws CustomPreconditionFailedException,
          CustomPreconditionErrorException,
          DatabaseException {
    // want at >= 5.7 and IS 5.7
    setUpDBVersion(new SemanticVersion(5, 7, null, null));
    precondition.setVersion("5.7");
    precondition.setOperator("gte");
    precondition.check(db);
  }

  @Test
  public void precondition_LT_Pass()
      throws CustomPreconditionFailedException,
          CustomPreconditionErrorException,
          DatabaseException {
    // want at < 5.7 and IS 5.6
    setUpDBVersion(new SemanticVersion(5, 6, null, null));
    precondition.setVersion("5.7");
    precondition.setOperator("lt");
    precondition.check(db);
  }

  @Test
  public void precondition_EQ_Pass()
      throws CustomPreconditionFailedException,
          CustomPreconditionErrorException,
          DatabaseException {
    setUpDBVersion(new SemanticVersion(5, 6, null, null));
    precondition.setVersion("5.6");
    precondition.setOperator("eq");
    precondition.check(db);
  }

  @Test
  public void precondition_EQ_Fail() throws CustomPreconditionErrorException, DatabaseException {
    setUpDBVersion(new SemanticVersion(5, 6, null, null));
    precondition.setVersion("5");
    precondition.setOperator("eq");
    assertThrows(CustomPreconditionFailedException.class, () -> precondition.check(db));
  }

  @Test
  public void tooNewDBFailure() throws CustomPreconditionErrorException, DatabaseException {
    // want 5.6 but is 5.7
    setUpDBVersion(new SemanticVersion(5, 7, null, null));
    precondition.setVersion("5.6");
    precondition.setOperator("lt");
    assertThrows(CustomPreconditionFailedException.class, () -> precondition.check(db));
  }

  private void setUpDBVersion(SemanticVersion version) throws DatabaseException {
    Mockito.when(db.getConnection()).thenReturn(conn);
    Mockito.when(conn.getDatabaseMajorVersion()).thenReturn(version.getMajor());
    Mockito.when(conn.getDatabaseMinorVersion()).thenReturn(version.getMinor());
  }
}
