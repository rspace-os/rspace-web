package com.researchspace.dao;

import static com.researchspace.testutils.CommsTestUtils.createARequest;
import static com.researchspace.testutils.CommsTestUtils.createAnyNotification;
import static org.junit.jupiter.api.Assertions.*;

import com.researchspace.model.User;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.Notification;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CommunicationSequenceTest extends BaseDaoTestCase {
  @Autowired private BasicDataSource dataSource;

  @Test
  void createsNotificationsAndTargetsWithNoSpareConnection() throws Exception {
    User user = createAndSaveUserIfNotExists("sequence-test-user");
    var session = sessionFactory.getCurrentSession();
    session.flush();
    // Acquire the transaction connection before saturating the pool. Both generators must
    // cross block boundaries, even if a preceding test has warmed their optimizers.
    session.doWork(connection -> assertFalse(connection.isClosed()));
    int maxTotal = dataSource.getMaxTotal();
    long maxWait = dataSource.getMaxWaitMillis();
    List<Connection> held = new ArrayList<>();
    while (dataSource.getNumIdle() > 0) {
      held.add(dataSource.getConnection());
    }
    dataSource.setMaxTotal(held.size() + 1);
    dataSource.setMaxWaitMillis(500);
    try {
      assertEquals(held.size() + 1, dataSource.getNumActive());
      Set<Long> communicationIds = new HashSet<>();
      Set<Long> targetIds = new HashSet<>();
      for (int i = 0; i < 121; i++) {
        Notification notification = createAnyNotification(user);
        CommunicationTarget target = new CommunicationTarget();
        target.setRecipient(user);
        notification.addRecipient(target);
        session.persist(notification);
        assertNotNull(notification.getId());
        assertNotNull(target.getId());
        assertTrue(communicationIds.add(notification.getId()));
        assertTrue(targetIds.add(target.getId()));
      }
      var request = createARequest(user);
      session.persist(request);
      assertTrue(communicationIds.add(request.getId()));
      session.flush();
      session.clear();
      for (Long id : communicationIds) {
        assertNotNull(session.get(Communication.class, id));
      }
      assertEquals(held.size() + 1, dataSource.getNumActive());
    } finally {
      dataSource.setMaxTotal(maxTotal);
      dataSource.setMaxWaitMillis(maxWait);
      for (Connection connection : held) {
        connection.close();
      }
    }
  }
}
