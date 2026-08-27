package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

public class SandboxTest extends SpringTransactionalTest {

  @Test
  public void testConcat() {
    String random = getRandomAlphabeticString("");
    String name = "abc_" + random + "_xyz";
    User expectedUser = createAndSaveUserIfNotExists(name);
    Session session = sessionFactory.getCurrentSession();
    User user =
        session
            .createQuery(
                "from User where username like concat('%abc_', :usernameSuffix)", User.class)
            .setParameter("usernameSuffix", random + "%")
            .uniqueResult();
    assertNotNull(user);

    String largeName = "1234" + name + "5678";
    user =
        session
            .createQuery("from User where :constant like concat('%', username, '%')", User.class)
            .setParameter("constant", largeName)
            .uniqueResult();
    assertNotNull(user);
    assertEquals(expectedUser, user);

    user =
        session
            .createQuery(
                "from User user where :constant like concat('%', user.username, '%')", User.class)
            .setParameter("constant", largeName)
            .uniqueResult();
    assertNotNull(user);
    assertEquals(expectedUser, user);
  }

  @Test
  public void testFilePropertyHqlWithSetParameterList() {
    Session session = sessionFactory.getCurrentSession();
    saveFileProperty(session, "a/b/c/12345.jpg");
    FileProperty backslashPath = saveFileProperty(session, "a\\b\\c\\12346.jpg");

    String pathWithoutBackslashes = backslashPath.getRelPath().replace("\\", "");
    FileProperty retrieved =
        session
            .createQuery(
                "from FileProperty where replace(replace(relPath, '/', ''), '\\\\', '') in"
                    + " :paths",
                FileProperty.class)
            .setParameterList("paths", List.of(pathWithoutBackslashes))
            .uniqueResult();

    assertEquals(backslashPath, retrieved);
  }

  @Test
  public void testFilePropertyHqlWithLike() {
    Session session = sessionFactory.getCurrentSession();
    FileProperty forwardSlashPath = saveFileProperty(session, "a/b/c/12345.jpg");
    FileProperty backslashPath = saveFileProperty(session, "a\\b\\c\\12346.jpg");
    String query =
        "from FileProperty fileProperty where :value like concat('%',"
            + " replace(replace(fileProperty.relPath, '/', ''), '\\\\', ''), '%')";

    FileProperty retrievedForwardSlashPath =
        session
            .createQuery(query, FileProperty.class)
            .setParameter("value", forwardSlashPath.getRelPath().replace("/", ""))
            .uniqueResult();
    assertEquals(forwardSlashPath, retrievedForwardSlashPath);

    String backslashPathWithoutSeparators = backslashPath.getRelPath().replace("\\", "");
    FileProperty retrievedBackslashPath =
        session
            .createQuery(query, FileProperty.class)
            .setParameter("value", backslashPathWithoutSeparators)
            .uniqueResult();
    assertEquals(backslashPath, retrievedBackslashPath);

    List<FileProperty> retrievedPaths =
        session
            .createQuery(
                "from FileProperty fileProperty where replace(replace(fileProperty.relPath, '/',"
                    + " ''), '\\\\', '') in (:paths)",
                FileProperty.class)
            .setParameterList(
                "paths",
                List.of(
                    forwardSlashPath.getRelPath().replace("/", ""), backslashPathWithoutSeparators))
            .list();
    assertEquals(2, retrievedPaths.size());
  }

  @Test
  public void testGetRelativePathFromAbsolutePath() {
    String absolutePath = "/abc/file_store/c/file_store/c/d/e";
    String relativePath = "c/d/e";

    String relativePathFromAbsolute =
        absolutePath.substring(absolutePath.lastIndexOf("file_store") + 11);

    assertEquals(relativePath, relativePathFromAbsolute);
  }

  private FileProperty saveFileProperty(Session session, String relativePath) {
    FileProperty fileProperty = new FileProperty();
    fileProperty.setRelPath(relativePath);
    return (FileProperty) session.merge(fileProperty);
  }
}
