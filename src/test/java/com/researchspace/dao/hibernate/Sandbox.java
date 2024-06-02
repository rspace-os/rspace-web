package com.researchspace.dao.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.researchspace.dao.CollaborationGroupTrackerDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.hibernate.type.StringType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class Sandbox extends SpringTransactionalTest {

  @Autowired CollaborationGroupTrackerDao trackerDao;

  @Autowired UserDao userDao;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testConcat() {
    String random = getRandomAlphabeticString("");
    String name = "abc_" + random + "_xyz";
    User u = createAndSaveUserIfNotExists(name);
    Session session = sessionFactory.getCurrentSession();
    User user =
        (User)
            session
                .createQuery("from User where username like concat('%abc_', ?)")
                .setString(0, random + '%')
                .uniqueResult();
    assertNotNull(user);

    String largeName = "1234" + name + "5678";
    user =
        (User)
            session
                .createQuery("from User where :constant like concat('%',username,'%')")
                .setParameter("constant", "'" + largeName + "'")
                .uniqueResult();
    assertNotNull(user);
    assertEquals(u, user);

    user =
        (User)
            session
                .createCriteria(User.class)
                .add(
                    Restrictions.sqlRestriction(
                        "? like concat('%',{alias}.username,'%')",
                        "'" + largeName + "'", StringType.INSTANCE))
                .uniqueResult();
    assertNotNull(user);
    assertEquals(u, user);
  }

  @Test
  public void testFilePropertyhql() {
    Session session = sessionFactory.getCurrentSession();
    FileProperty fp = new FileProperty();
    String relPath = "a/b/c/12345.jpg";
    fp.setRelPath(relPath);
    //	fp.setFileUri("c:/ab/c" + relPath);
    fp = (FileProperty) session.merge(fp);

    FileProperty fp3 = new FileProperty();
    String relPath3 = "a\\b\\c\\12346.jpg";
    fp3.setRelPath(relPath3);
    //	fp3.setFileUri("c:/ab/c" + relPath3);
    fp3 = (FileProperty) session.merge(fp3);

    String altered = relPath3.replaceAll("\\\\", "");
    String[] paths = new String[] {altered};
    FileProperty retrieved =
        (FileProperty)
            session
                .createQuery(
                    " from FileProperty where replace(replace(relPath,'/',''),'\\\\','') in :query")
                .setParameterList("query", paths)
                .uniqueResult();
    assertEquals(fp3, retrieved);
  }

  @Test
  public void testFileProperty() {
    Session session = sessionFactory.getCurrentSession();
    FileProperty fp = new FileProperty();
    String relPath = "a/b/c/12345.jpg";
    fp.setRelPath(relPath);

    fp = (FileProperty) session.merge(fp);

    FileProperty fp3 = new FileProperty();
    String relPath3 = "a\\b\\c\\12346.jpg";
    fp3.setRelPath(relPath3);

    fp3 = (FileProperty) session.merge(fp3);

    String altered = "a\\b\\c\\12345.jpg".replaceAll("\\\\", "");
    String sql = "? like concat('%',replace(replace({alias}.relPath,'/',''),'\\\\',''), '%')";
    FileProperty retrieved =
        (FileProperty)
            session
                .createCriteria(FileProperty.class)
                .add(Restrictions.sqlRestriction(sql, "'" + altered + "'", StringType.INSTANCE))
                .uniqueResult();
    assertEquals(fp, retrieved);

    String altered3 = relPath3.replaceAll("\\\\", "");

    FileProperty retrieved3 =
        (FileProperty)
            session
                .createCriteria(FileProperty.class)
                .add(Restrictions.sqlRestriction(sql, "'" + altered3 + "'", StringType.INSTANCE))
                .uniqueResult();
    assertEquals(fp3, retrieved3);

    // and search other way round
    String[] paths = new String[] {"'" + altered + "'", "'" + altered3 + "'"};
    List results =
        session
            .createCriteria(FileProperty.class)
            .add(
                Restrictions.sqlRestriction(
                    "replace(replace({alias}.relPath,'/',''),'\\\\','') in ("
                        + StringUtils.join(paths, ",")
                        + ")"))
            .list();
    assertEquals(2, results.size());
  }

  @Test
  public void testGetRelFromAbs() {
    String abs1 = "/abc/file_store/c/file_store/c/d/e";
    String rel1 = "c/d/e";
    String relFromAbs = abs1.substring(abs1.lastIndexOf("file_store") + 11);
    assertEquals(relFromAbs, rel1, relFromAbs);
  }
}
