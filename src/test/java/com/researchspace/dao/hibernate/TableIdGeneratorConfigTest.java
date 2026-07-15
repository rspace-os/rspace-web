package com.researchspace.dao.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.dao.BaseDaoTestCase;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.field.Field;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.record.RSForm;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.generator.Generator;
import org.hibernate.id.enhanced.TableGenerator;
import org.hibernate.metamodel.MappingMetamodel;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Guards the id-generation layout for TABLE-strategy entities: each entity allocates from its own
 * row in hibernate_sequences, keyed by its physical table name, except the two TABLE_PER_CLASS
 * hierarchies whose sibling tables must share one counter. A bare {@code @GeneratedValue(strategy =
 * TABLE)} without a named generator would silently allocate from a shared "default" segment
 * instead, interleaving ids across unrelated tables.
 */
public class TableIdGeneratorConfigTest extends BaseDaoTestCase {

  @Autowired SessionFactory sessionFactory;

  @Test
  public void noTableStrategyEntityUsesTheSharedDefaultSegment() {
    List<String> offenders = new ArrayList<>();
    mappingMetamodel()
        .forEachEntityDescriptor(
            descriptor -> {
              Generator generator = descriptor.getGenerator();
              if (generator instanceof TableGenerator tableGenerator
                  && "default".equals(tableGenerator.getSegmentValue())) {
                offenders.add(descriptor.getEntityName());
              }
            });
    assertTrue(
        "Entities allocating ids from the shared \"default\" segment (add a named @TableGenerator"
            + " to each): "
            + offenders,
        offenders.isEmpty());
  }

  @Test
  public void tableStrategyEntitiesKeyTheirOwnTableRow() {
    assertEquals("Sample", segmentOf(Sample.class));
    assertEquals("SubSample", segmentOf(SubSample.class));
    assertEquals("Container", segmentOf(Container.class));
    assertEquals("Field", segmentOf(Field.class));
    assertEquals("RSForm", segmentOf(RSForm.class));
  }

  @Test
  public void tablePerClassHierarchiesShareOneCounterAcrossSiblingTables() {
    assertEquals("AbstractUserOrGroupImpl", segmentOf(User.class));
    assertEquals("AbstractUserOrGroupImpl", segmentOf(Group.class));
    assertEquals("Communication", segmentOf(Notification.class));
  }

  @Test
  public void allocationBlockSizesMatchTheMigrationReseedMargin() {
    assertEquals(50, tableGeneratorOf(Sample.class).getOptimizer().getIncrementSize());
    assertEquals(1, tableGeneratorOf(User.class).getOptimizer().getIncrementSize());
  }

  private String segmentOf(Class<?> entityClass) {
    return tableGeneratorOf(entityClass).getSegmentValue();
  }

  private TableGenerator tableGeneratorOf(Class<?> entityClass) {
    Generator generator = mappingMetamodel().getEntityDescriptor(entityClass).getGenerator();
    assertNotEquals(entityClass + " has no generator", null, generator);
    assertTrue(
        entityClass + " expected a TableGenerator but was " + generator.getClass(),
        generator instanceof TableGenerator);
    return (TableGenerator) generator;
  }

  private MappingMetamodel mappingMetamodel() {
    return sessionFactory.unwrap(SessionFactoryImplementor.class).getMappingMetamodel();
  }
}
