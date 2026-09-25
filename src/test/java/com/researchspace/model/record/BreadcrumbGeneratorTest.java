package com.researchspace.model.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BreadcrumbGeneratorTest {

  private DefaultBreadcrumbGenerator bGen;

  @BeforeEach
  public void setUp() throws Exception {
    bGen = new DefaultBreadcrumbGenerator();
  }

  @Test
  public void testGenerateBreadcrumbNoNull1stArg() {
    Folder folder = TestFactory.createAFolder("any", createAUser());
    assertThrows(IllegalArgumentException.class, () -> bGen.generateBreadcrumb(null, folder));
  }

  @Test
  public void testGenerateBreadcrumbNoNull2ndArg() {
    Folder folder = TestFactory.createAFolder("any", createAUser());
    assertThrows(IllegalArgumentException.class, () -> bGen.generateBreadcrumb(folder, null));
  }

  private User createAUser() {
    return TestFactory.createAnyUser("user");
  }

  @Test
  public void testGenerateBreadcrumbForSingleFolder() {
    User u1 = createAUser();
    Folder single = TestFactory.createAFolder("name", u1);
    single.setId(1L);

    Breadcrumb bcrumb = bGen.generateBreadcrumb(single, single);
    assertTrue(bcrumb.isContainLinks());
    assertThat(bcrumb.getElements()).hasSize(1);
    assertEquals(single.getName(), bcrumb.getElements().get(0).getDisplayname());
  }

  @Test
  public void testGenerateBreadcrumbForNestedFolder()
      throws InterruptedException, IllegalAddChildOperation {
    User u1 = createAUser();
    Folder parent = TestFactory.createAFolder("parent", u1);
    Thread.sleep(1);

    Folder child = TestFactory.createAFolder("child", u1);
    parent.setId(1L);
    child.setId(2L);
    parent.addChild(child, u1, true);
    Thread.sleep(1);

    Breadcrumb bcrumb = bGen.generateBreadcrumb(child, parent);
    assertTrue(bcrumb.isContainLinks());
    assertThat(bcrumb.getElements()).hasSize(2);

    Folder gchild = TestFactory.createAFolder("gchild", u1);
    gchild.setId(3L);
    child.addChild(gchild, u1, true);

    Breadcrumb bCrumb2 = bGen.generateBreadcrumb(gchild, parent);
    assertThat(bCrumb2.getElements()).hasSize(3);

    Folder bothChildAndGChild = TestFactory.createAFolder("bothChildAndGChild", u1);
    bothChildAndGChild.setId(4L);
    parent.addChild(bothChildAndGChild, u1, true);
    child.addChild(bothChildAndGChild, u1, true);

    Breadcrumb bCrumb3 = bGen.generateBreadcrumb(bothChildAndGChild, parent);
    assertThat(bCrumb3.getElements()).hasSize(2);
  }

  @Test
  public void testGenerateHomeFolderBreadcrumbs()
      throws InterruptedException, IllegalAddChildOperation {
    User user = createAUser();
    Folder parent = TestFactory.createAFolder("parent", user);
    parent.addType(RecordType.ROOT);
    user.setRootFolder(parent);
    Thread.sleep(1);

    Folder child = TestFactory.createAFolder("child", user);
    parent.setId(1L);
    child.setId(2L);
    parent.addChild(child, user, true);
    Thread.sleep(1);

    Breadcrumb bcrumb = bGen.generateBreadcrumbToHome(child, parent, null);
    assertTrue(bcrumb.isContainLinks());
    assertThat(bcrumb.getElements()).hasSize(2);
    assertEquals(
        BreadcrumbGenerator.HOME_FOLDER_DISPLAY_NAME, bcrumb.getElements().get(0).getDisplayname());
  }
}
