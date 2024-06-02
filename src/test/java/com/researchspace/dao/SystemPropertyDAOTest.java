package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.researchspace.model.PropertyDescriptor;
import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyTestFactory;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SystemPropertyDAOTest extends SpringTransactionalTest {

  @Autowired private SystemPropertyDao dao;

  @Test
  public void basicSaveLoad() {
    SystemProperty sysprop = createSystemProperty();
    SystemProperty sysprop2 = createSystemProperty();
    sysprop2.getDescriptor().setName("other");
    sysprop.setDependent(sysprop2);
    SystemPropertyValue value = new SystemPropertyValue(sysprop, "false");
    value = dao.save(value);
    SystemPropertyValue found = dao.get(value.getId());
    assertEquals(value, found);
    assertNotNull(found.getProperty());
    assertNotNull(found.getProperty().getDependent());
  }

  private SystemProperty createSystemProperty() {
    PropertyDescriptor prop = SystemPropertyTestFactory.createAPropertyDescriptor();
    prop = (PropertyDescriptor) sessionFactory.getCurrentSession().merge(prop);
    return new SystemProperty(prop);
  }

  @Test
  public void findByPropertyName() {
    assertNull(dao.findByPropertyName("unknown"));
    String KNOWN_PROPERTY = "box.available";
    SystemProperty sysprop = dao.findPropertyByPropertyName(KNOWN_PROPERTY);
    assertNotNull(sysprop);
    SystemPropertyValue syspropvalue = dao.findByPropertyName(KNOWN_PROPERTY);
    assertNotNull(syspropvalue);
  }
}
