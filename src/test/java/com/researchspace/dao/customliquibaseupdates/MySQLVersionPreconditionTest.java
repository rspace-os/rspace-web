package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.core.util.version.SemanticVersion;
import liquibase.database.Database;
import liquibase.database.DatabaseConnection;
import liquibase.exception.CustomPreconditionErrorException;
import liquibase.exception.CustomPreconditionFailedException;
import liquibase.exception.DatabaseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class MySQLVersionPreconditionTest {

  public @Rule MockitoRule mockito = MockitoJUnit.rule();

  @Mock Database db;
  @Mock DatabaseConnection conn;
  MySQLVersionPrecondition precondition;

  @Before
  public void setUp() throws Exception {
    precondition = new MySQLVersionPrecondition();
  }

  @After
  public void tearDown() throws Exception {}

  @Test(expected = IllegalArgumentException.class)
  public void invalidOperatorCausesError() throws DatabaseException {
    setUpDBVersion(new SemanticVersion(5, 6, null, null));
    precondition.setVersion("5.7");
    precondition.setOperator("notanoperator");
  }

  @Test(expected = CustomPreconditionFailedException.class)
  public void tooOldDBFailure()
      throws CustomPreconditionFailedException,
          CustomPreconditionErrorException,
          DatabaseException {
    // want 5.7 but is 5.6
    setUpDBVersion(new SemanticVersion(5, 6, null, null));
    precondition.setVersion("5.7");
    precondition.setOperator("gte");
    // fails - should ne > =5.7 but is 5.6
    precondition.check(db);
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

  @Test(expected = CustomPreconditionFailedException.class)
  public void precondition_EQ_Fail()
      throws CustomPreconditionFailedException,
          CustomPreconditionErrorException,
          DatabaseException {
    setUpDBVersion(new SemanticVersion(5, 6, null, null));
    precondition.setVersion("5");
    precondition.setOperator("eq");
    precondition.check(db);
  }

  @Test(expected = CustomPreconditionFailedException.class)
  public void tooNewDBFailure()
      throws CustomPreconditionFailedException,
          CustomPreconditionErrorException,
          DatabaseException {
    // want 5.6 but is 5.7
    setUpDBVersion(new SemanticVersion(5, 7, null, null));
    precondition.setVersion("5.6");
    precondition.setOperator("lt");

    precondition.check(db);
  }

  private void setUpDBVersion(SemanticVersion version) throws DatabaseException {
    Mockito.when(db.getConnection()).thenReturn(conn);
    Mockito.when(conn.getDatabaseMajorVersion()).thenReturn(version.getMajor());
    Mockito.when(conn.getDatabaseMinorVersion()).thenReturn(version.getMinor());
  }
}
