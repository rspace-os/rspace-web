package com.researchspace.model.record;

import static com.researchspace.model.record.TestFactory.createAFolder;
import static com.researchspace.model.record.TestFactory.createAnySD;
import static com.researchspace.model.record.TestFactory.createAnyUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RSPathTest {

  Folder parent;
  Folder middle;
  Folder child;
  StructuredDocument record;
  User user;

  @BeforeEach
  public void setUp() throws Exception {
    user = createAnyUser("any");
    parent = createAFolder("parent", user);
    middle = createAFolder("middle", user);
    child = createAFolder("child", user);
    record = createAnySD();
    parent.addChild(middle, user, true);
    middle.addChild(child, user, true);
    child.addChild(record, user, true);
    user.setRootFolder(parent);
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void findByType() {
    RSPath path = record.getParentHierarchyForUser(user);
    assertThat(path.findFirstByType(RecordType.SYSTEM)).isNotPresent();
    assertEquals(parent, path.findFirstByType(RecordType.FOLDER).get());
  }

  @Test
  public void testRSPath() {
    RSPath path = record.getParentHierarchyForUser(user);
    assertThat(path).hasSize(4);
    assertEquals(parent, path.getFirstElement().get());
    assertEquals(record, path.getLastElement().get());

    assertThat(path.getImmediateParentOf(parent)).isNotPresent();
    assertEquals(child, path.getImmediateParentOf(record).get());

    assertThat(path.get(1000)).isNotPresent(); // returns empty optional rather than throw exception
    path.toString();
  }

  @Test
  public void testRSPathConstructor() {
    // test does not throw NPEs when is a nempty list
    RSPath path = new RSPath(null); //
    assertThat(path).isEmpty();
    assertThat(path.getFirstElement()).isNotPresent();
    assertThat(path.getLastElement()).isNotPresent();
    assertThat(path).isEmpty();
    assertNotNull(path.iterator());
    Folder unknown = createAFolder("notinpath", user);
    assertThat(path.getImmediateParentOf(unknown)).isNotPresent();

    assertThat(path.get(-1)).isNotPresent();
    path.toString();
  }

  @Test
  public void testRSPathConstructorChecksContents() {

    // single element ok
    List<BaseRecord> validPath = Arrays.asList(new BaseRecord[] {record});
    new RSPath(validPath);

    // wrong way round
    List<BaseRecord> invalidPath = Arrays.asList(new BaseRecord[] {record, parent});
    assertThrows(IllegalArgumentException.class, () -> new RSPath(invalidPath));
  }

  @Test
  public void testMerge() {
    List<BaseRecord> srcToVia = new ArrayList<>();
    List<BaseRecord> viaToTarget = new ArrayList<>();
    parent.removeChild(middle);
    record.setParents(new HashSet<>());
    parent.addChild(record, user, true);
    srcToVia.add(parent);
    srcToVia.add(record);
    viaToTarget.add(parent);
    RSPath p1 = new RSPath(srcToVia);
    RSPath p2 = new RSPath(viaToTarget);
    // test is symmetrical
    assertThat(p1.merge(p2)).hasSize(2);
    assertThat(p2.merge(p1)).hasSize(2);
  }
}
