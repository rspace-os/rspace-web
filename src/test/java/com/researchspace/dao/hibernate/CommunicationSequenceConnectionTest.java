package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.relational.QualifiedName;
import org.hibernate.boot.model.relational.QualifiedNameImpl;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.dialect.MariaDB106Dialect;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.enhanced.SequenceStructure;
import org.junit.jupiter.api.Test;

class CommunicationSequenceConnectionTest {
  @Test
  void nextValueUsesSessionJdbcCoordinatorWithoutAnIsolationDelegate() throws Exception {
    QualifiedName name =
        new QualifiedNameImpl(null, null, Identifier.toIdentifier("communication_seq"));
    var structure =
        new SequenceStructure(mock(JdbcEnvironment.class), "test", name, 1, 50, Long.class) {
          {
            physicalSequenceName = name;
          }
        };
    var context = mock(SqlStringGenerationContext.class);
    when(context.getDialect()).thenReturn(new MariaDB106Dialect());
    when(context.format(name)).thenReturn("communication_seq");
    structure.initialize(context);
    String sql = "select next value for communication_seq";
    var session = mock(SharedSessionContractImplementor.class, RETURNS_DEEP_STUBS);
    var statement = mock(PreparedStatement.class);
    var result = mock(ResultSet.class);
    when(session.getJdbcCoordinator().getStatementPreparer().prepareStatement(sql))
        .thenReturn(statement);
    when(session.getJdbcCoordinator().getResultSetReturn().extract(statement, sql))
        .thenReturn(result);
    when(result.getLong(1)).thenReturn(100L);

    assertEquals(100L, structure.buildCallback(session).getNextValue().makeValue());
    verify(session.getJdbcCoordinator().getStatementPreparer()).prepareStatement(sql);
    verify(session, never()).getTransactionCoordinator();
  }
}
