package com.researchspace.dao;

import static com.researchspace.core.util.TransformerUtils.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.PropertyDescriptor;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElement;
import com.researchspace.model.apps.AppConfigElementDescriptor;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.system.SystemPropertyTestFactory;
import com.researchspace.testutils.SpringTransactionalTest;
import org.hibernate.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.jdbc.Sql;

@Sql(
    statements = {
      "insert into App values (-55, 1, 'slack channel', 'slack.app')",
      "insert into PropertyDescriptor values (-100, '', 'slackChannelName', '2')",
      "insert into AppConfigElementDescriptor (id, descriptor_id,app_id) values (-100,-100, -55)"
    })
public class UserAppConfigDaoTest extends SpringTransactionalTest {

  private @Autowired UserAppConfigDao dao;

  App app;
  User u1;
  User u2;
  ;

  @Before
  public void setUp() throws Exception {
    app = (App) getSession().get(App.class, -55L);
    u1 = TestFactory.createAnyUser("u1");
    u2 = TestFactory.createAnyUser("u2");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void storeNewElement() {
    u1 = createAndSaveRandomUser();

    assertEquals("slack channel", app.getLabel());

    String propName = "slackChannelName";
    String propValue = "channel1";
    UserAppConfig cfg = dao.findByPropertyNameUser(propName, u1);
    assertNull(cfg);

    app = dao.findAppByPropertyName(propName);
    assertNotNull(app);

    cfg = new UserAppConfig(u1, app, true);
    AppConfigElementDescriptor desc = app.getDescriptorByName(propName);
    assertNotNull(desc);
    AppConfigElement value = new AppConfigElement(desc, propValue);
    AppConfigElementSet set = new AppConfigElementSet();
    set.setConfigElements(toSet(value));
    cfg.addConfigSet(set);
    cfg = dao.save(cfg);
    // config now persisted
    assertNotNull(dao.findByPropertyNameUser(propName, u1));
  }

  private Session getSession() {
    return sessionFactory.getCurrentSession();
  }

  @Test
  public void testFindAppConfigForUser() {
    u1 = createAndSaveRandomUser();
    UserAppConfig c1 = new UserAppConfig(u1, app, true);
    c1 = dao.save(c1);
    assertNotNull(c1.getId());
    assertEquals(app, c1.getApp());
    PropertyDescriptor prop = storeProperty();
    AppConfigElementDescriptor descriptor = storeDescriptor(prop);
    AppConfigElement el = new AppConfigElement(descriptor);
    el.setValue("true");
    AppConfigElementSet elSet = new AppConfigElementSet();
    elSet.setConfigElements(TransformerUtils.toSet(el));
    u2 = createAndSaveRandomUser();
    UserAppConfig c2 = new UserAppConfig(u2, app, true);
    c2.addConfigSet(elSet);
    c2 = dao.save(c2);

    // now load from Id
    c2 = dao.get(c2.getId());
    assertEquals(1, c2.getConfigElementSetCount());
    Long elSetId = c2.getAppConfigElementSets().iterator().next().getId();
    assertNotNull(elSetId);
    Long elId =
        c2.getAppConfigElementSets()
            .iterator()
            .next()
            .getConfigElements()
            .iterator()
            .next()
            .getId();
    assertNotNull(elSetId);
    assertNotNull(elId);
    // check that remove removes completely child elements
    assertTrue(c2.removeConfigSet(elSet));
    c2 = dao.save(c2);
    assertEquals(0, c2.getConfigElementSetCount());
  }

  @Test(expected = DataIntegrityViolationException.class)
  public void noNullApp() {
    u1 = createAndSaveRandomUser();
    // null app not allowed
    UserAppConfig c1 = new UserAppConfig(u1, null, true);
    c1 = dao.save(c1);
  }

  @Test(expected = DataIntegrityViolationException.class)
  public void noNullUser() {
    u1 = createAndSaveRandomUser();
    // null app not allowed
    UserAppConfig c1 = new UserAppConfig(null, app, true);
    c1 = dao.save(c1);
  }

  private AppConfigElementDescriptor storeDescriptor(PropertyDescriptor prop) {
    AppConfigElementDescriptor rc = new AppConfigElementDescriptor(prop);
    return (AppConfigElementDescriptor) getSession().merge(rc);
  }

  private PropertyDescriptor storeProperty() {
    PropertyDescriptor prop = SystemPropertyTestFactory.createAPropertyDescriptor();
    return (PropertyDescriptor) getSession().merge(prop);
  }
}
