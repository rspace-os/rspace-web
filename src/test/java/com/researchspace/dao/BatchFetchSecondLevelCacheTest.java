package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.dtos.DateFieldDTO;
import com.researchspace.model.field.DateFieldForm;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Set;
import java.util.TreeSet;
import org.hibernate.Session;
import org.hibernate.engine.FetchTiming;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.internal.ToOneAttributeMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

/**
 * Batch fetching with the production second-level cache settings, which the shared test context
 * turns off. With hibernate.default_batch_fetch_size set, Hibernate 6.4 resolves an EAGER to-one
 * whose owner is L2-cacheable as a proxy (batch-initialised before the query returns) instead of
 * the real instance. A proxy of a class with subclasses cannot be cast to a subclass.
 */
@ContextConfiguration(locations = "classpath:applicationContext-dao-l2cache.xml")
public class BatchFetchSecondLevelCacheTest extends SpringTransactionalTest {

  @AfterEach
  void evictSecondLevelCache() {
    // test transactions roll back, so drop anything cached from uncommitted rows
    sessionFactory.getCache().evictAllRegions();
  }

  @Test
  public void fieldFormIsTheOnlyCacheableOwnerOfAnEagerToOneWithSubclasses() {
    Set<String> found = new TreeSet<>();
    sessionFactory
        .unwrap(SessionFactoryImplementor.class)
        .getMappingMetamodel()
        .forEachEntityDescriptor(
            owner -> {
              if (owner.getCacheAccessStrategy() == null) {
                return;
              }
              owner.forEachAttributeMapping(
                  attribute -> {
                    if (attribute instanceof ToOneAttributeMapping toOne
                        && toOne.getTiming() == FetchTiming.IMMEDIATE) {
                      EntityPersister target = toOne.getEntityMappingType().getEntityPersister();
                      if (target.getEntityMetamodel().hasSubclasses()) {
                        found.add(
                            toOne.findContainingEntityMapping().getEntityName()
                                + "."
                                + toOne.getAttributeName());
                      }
                    }
                  });
            });

    assertEquals(
        Set.of(FieldForm.class.getName() + ".form"),
        found,
        "An EAGER to-one from an L2-cacheable entity to a class with subclasses can load as a"
            + " proxy of the declared type. Unproxy it before casting to a subclass (see"
            + " AbstractFormManagerImpl), then update this list.");
  }

  @Test
  public void fieldFormChangesWorkWhenTheFieldFormsFormIsAProxy() {
    assertTrue(sessionFactory.getSessionFactoryOptions().isSecondLevelCacheEnabled());
    User user = createInitAndLoginAnyUser();
    RSForm form = formMgr.create(user);
    DateFieldForm fieldForm = formMgr.createFieldForm(dateField("date"), form.getId(), user);
    flushAndClear();

    loadFieldFormByQuery(fieldForm.getId());
    assertDoesNotThrow(() -> formMgr.updateFieldForm(dateField("date2"), fieldForm.getId(), user));
    flushAndClear();

    FieldForm loaded = loadFieldFormByQuery(fieldForm.getId());
    assertInstanceOf(HibernateProxy.class, loaded.getForm());
    assertDoesNotThrow(() -> formMgr.deleteFieldFromForm(fieldForm.getId(), user));
  }

  @Test
  public void recordToFolderTargetsAreRealInstancesForSubclassCasts() throws Exception {
    User user = createInitAndLoginAnyUser();
    Folder root = folderDao.getRootRecordForUser(user);
    Folder parent = createFolder("parent", root, user);
    createFolder("child", parent, user);
    Notebook notebook = createNotebookWithNEntries(parent.getId(), "notebook", 1, user);
    flushAndClear();

    assertEquals(2, folderDao.get(parent.getId()).getSubfolders().size());
    BaseRecord entry = folderMgr.getNotebook(notebook.getId()).getChildrens().iterator().next();
    assertEquals(1, entry.getParentNotebooks().size());
    flushAndClear();

    assertDoesNotThrow(() -> recordDeletionMgr.deleteFolder(root.getId(), parent.getId(), user));
  }

  private FieldForm loadFieldFormByQuery(Long id) {
    return session()
        .createQuery("from FieldForm where id = :id", FieldForm.class)
        .setParameter("id", id)
        .uniqueResult();
  }

  private static DateFieldDTO<DateFieldForm> dateField(String name) {
    return new DateFieldDTO<>("", "", "", "yy-mm-dd", name);
  }

  private void flushAndClear() {
    session().flush();
    session().clear();
  }

  private Session session() {
    return sessionFactory.getCurrentSession();
  }
}
