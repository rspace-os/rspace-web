package com.researchspace.dao;

import static com.researchspace.core.util.TransformerUtils.toSet;
import static com.researchspace.testutils.CommsTestUtils.createRequestOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.UserProfile;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.dtos.MessageTypeFilter;
import com.researchspace.model.field.Field;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Behaviour of hibernate.default_batch_fetch_size (hibernate.cfg.xml). */
public class BatchFetchTest extends SpringTransactionalTest {

  private @Autowired CommunicationDao communicationDao;

  @Test
  public void initialisingOneLazyCollectionBatchLoadsTheSameRoleForOtherEntities() {
    User first = createAndSaveRandomUser();
    User second = createAndSaveRandomUser();
    flushAndClear();

    Map<Long, User> users =
        byId(
            session()
                .createQuery("from User where id in :ids", User.class)
                .setParameter("ids", List.of(first.getId(), second.getId()))
                .list(),
            User::getId);
    Hibernate.initialize(users.get(first.getId()).getUserPreferences());

    assertTrue(Hibernate.isInitialized(users.get(second.getId()).getUserPreferences()));
  }

  // A batch-loaded EAGER to-one is null while the owner's properties are set, and is set after.
  @Test
  public void queriesLoadEntitiesWhoseSettersCheckABatchLoadedAssociation() {
    User user = createInitAndLoginAnyUser();
    Long profileId = userDao.saveUserProfile(new UserProfile(user)).getId();
    Field field = createBasicDocumentInRootFolderWithText(user, "text").getFields().get(0);
    flushAndClear();

    assertEquals(user.getId(), userDao.getUserProfileById(profileId).getOwner().getId());
    Field loaded =
        session()
            .createQuery("from Field where id = :id", Field.class)
            .setParameter("id", field.getId())
            .uniqueResult();
    assertEquals(field.getFieldForm().getId(), loaded.getFieldForm().getId());
  }

  @Test
  public void listingMessagesDoesNotLoadGlobalMessageRecipients() {
    User sender = createAndSaveRandomUser();
    User recipient = createAndSaveRandomUser();
    MessageOrRequest global = saveMessage(MessageType.GLOBAL_MESSAGE, sender, recipient);
    MessageOrRequest first = saveMessage(MessageType.SIMPLE_MESSAGE, sender, recipient);
    MessageOrRequest second = saveMessage(MessageType.SIMPLE_MESSAGE, sender, recipient);
    flushAndClear();

    Map<Long, MessageOrRequest> listed =
        byId(
            communicationDao
                .getActiveRequestsAndMessagesForUser(
                    recipient,
                    PaginationCriteria.createDefaultForClass(CommunicationTarget.class),
                    MessageTypeFilter.ALL_TYPES)
                .getResults(),
            MessageOrRequest::getId);

    assertTrue(Hibernate.isInitialized(listed.get(first.getId()).getRecipients()));
    assertTrue(Hibernate.isInitialized(listed.get(second.getId()).getRecipients()));
    assertFalse(Hibernate.isInitialized(listed.get(global.getId()).getRecipients()));
  }

  private MessageOrRequest saveMessage(MessageType type, User sender, User recipient) {
    MessageOrRequest message = createRequestOfType(sender, type);
    CommunicationTarget target = new CommunicationTarget();
    target.setCommunication(message);
    target.setRecipient(recipient);
    target.setStatus(CommunicationStatus.NEW);
    message.setRecipients(toSet(target));
    return (MessageOrRequest) communicationDao.save(message);
  }

  private static <T> Map<Long, T> byId(List<T> entities, Function<T, Long> id) {
    return entities.stream().collect(Collectors.toMap(id, Function.identity()));
  }

  private void flushAndClear() {
    session().flush();
    session().clear();
  }

  private Session session() {
    return sessionFactory.getCurrentSession();
  }
}
