package com.researchspace.recordsandbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.RSPath;
import com.researchspace.model.record.TestFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FolderRelnTest {

  static User owner, user2, u3, u4, ROOT;
  static Folder p1, p2, p3, p4, ROOT_NODE, a, b, c, d, cP;

  @Before
  public void setUp() throws Exception {
    owner = TestFactory.createAnyUser("user1");
    u3 = TestFactory.createAnyUser("u3");
    u4 = TestFactory.createAnyUser("u4");
    ROOT = TestFactory.createAnyUser("ROOT");
    user2 = TestFactory.createAnyUser("user2");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testAddRemoveChild() throws IllegalAddChildOperation {
    Folder parent = TestFactory.createAFolder("1", owner);
    Folder child = TestFactory.createAFolder("2", owner);
    parent.addChild(child, owner);

    assertEquals(1, parent.getChildrens().size());
    assertEquals(child, parent.getChildrens().iterator().next());

    assertEquals(1, child.getParentFolders().size());
    assertEquals(parent, child.getParentFolders().iterator().next());

    assertTrue(parent.removeChild(child));
    assertEquals(0, parent.getChildrens().size());
    assertEquals(0, child.getParentFolders().size());

    assertFalse(parent.removeChild(TestFactory.createAFolder("3", owner)));
  }

  @Test(expected = IllegalAddChildOperation.class)
  public void testAddRemoveChildNoSelfEdges() throws IllegalAddChildOperation {
    Folder parent = TestFactory.createAFolder("1", owner);
    Folder child = TestFactory.createAFolder("2", owner);
    parent.addChild(parent, owner);
  }

  @Test(expected = IllegalAddChildOperation.class)
  public void testCannotCreateSimpleCycles() throws IllegalAddChildOperation {
    Folder parent = TestFactory.createAFolder("1", owner);
    Folder child = TestFactory.createAFolder("2", owner);
    Folder grandchild = TestFactory.createAFolder("3", owner);

    parent.addChild(child, owner);
    child.addChild(grandchild, owner);

    assertNull(grandchild.addChild(parent, owner));
    assertNull(grandchild.addChild(child, owner));
  }

  @Test(expected = IllegalAddChildOperation.class)
  public void testCannotCreateCyclesWithMultipleParents() throws IllegalAddChildOperation {

    Folder parent = TestFactory.createAFolder("1", owner);
    Folder parent2 = TestFactory.createAFolder("2", owner);
    Folder child = TestFactory.createAFolder("3", owner);
    parent.addChild(child, owner);
    parent2.addChild(child, owner);

    assertNull(child.addChild(parent2, owner));
  }

  @Test
  public void testCannotAddTwoEdgesWithSameNodesButDifferentOwners()
      throws IllegalAddChildOperation {
    Folder parent = TestFactory.createAFolder("1", owner);
    Folder child = TestFactory.createAFolder("2", owner);
    parent.addChild(child, owner);
    User other = TestFactory.createAnyUser("other");

    assertNull(parent.addChild(child, other));
  }

  @Test
  public void testFindTargetVia() throws IllegalAddChildOperation {
    Folder u1 = TestFactory.createAFolder("u1Home", owner);
    Folder u2 = TestFactory.createAFolder("u2Home", user2);
    Folder u1shared = TestFactory.createAFolder("u1shared", owner);
    Folder u2shared = TestFactory.createAFolder("u2shared", user2);
    Folder u1Labgroups = TestFactory.createAFolder("u1Labgroups", owner);
    Folder u2Labgroups = TestFactory.createAFolder("u2Labgroups", user2);
    Folder groupShared = TestFactory.createAFolder("groupShared", owner);
    Folder f1 = TestFactory.createAFolder("f1", user2);
    Folder node = TestFactory.createAFolder("src", user2);

    // set up structure:
    u1.addChild(u1shared, owner);
    u1shared.addChild(u1Labgroups, owner);
    u1Labgroups.addChild(groupShared, owner);
    u2.addChild(u2shared, user2);
    u2shared.addChild(u2Labgroups, user2);
    u2Labgroups.addChild(groupShared, user2);
    groupShared.addChild(f1, user2);
    f1.addChild(node, owner);
    u2.addChild(node, user2);
    u1Labgroups.addChild(u2, owner);

    RSPath shortest = node.getShortestPathToParent(u1shared);
    assertEquals(4, shortest.size());
    System.err.println(shortest);

    RSPath shortestVia = node.getShortestPathToParentVia(u1shared, null, groupShared);
    assertEquals(5, shortestVia.size());
    assertTrue(shortestVia.contains(groupShared));

    shortestVia =
        node.getShortestPathToParentVia(
            u1shared, null, TestFactory.createAFolder("notonPath", owner));
    assertEquals(0, shortestVia.size());
    assertTrue(shortestVia.isEmpty());
  }

  @Test
  public void testFindTarget() throws IllegalAddChildOperation {
    // u1<-x
    // u2<-g <- f1 <- x
    // search for g from x, should be 3 elements

    Folder u1 = TestFactory.createAFolder("1", owner);
    Folder u2 = TestFactory.createAFolder("2", user2);
    Folder g = TestFactory.createAFolder("g", user2);
    Folder f1 = TestFactory.createAFolder("f1", user2);
    Folder x = TestFactory.createAFolder("x", owner);

    Folder unknown = TestFactory.createAFolder("unknown", owner);
    u1.addChild(x, owner);
    u2.addChild(g, user2);
    g.addChild(f1, user2);
    f1.addChild(x, user2);

    assertTrue(x.getShortestPathToParent(unknown).isEmpty());
    // shortest path to itself includes itself
    assertEquals(1, x.getShortestPathToParent(x).size());

    RSPath sp1 = x.getShortestPathToParent(u1);
    assertEquals(2, sp1.size());
    assertEquals(u1, sp1.getFirstElement().get());

    RSPath sp2 = x.getShortestPathToParent(g);
    assertEquals(3, sp2.size());
    assertEquals(g, sp2.getFirstElement().get());
    assertEquals(f1, sp2.get(1).get());
    assertEquals(x, sp2.getLastElement().get());
  }

  @Test
  public void testHierarchy() throws IllegalAddChildOperation {
    Folder parent = TestFactory.createAFolder("1", owner);
    parent.addType(RecordType.ROOT);
    owner.setRootFolder(parent);
    assertEquals(1, parent.getParentHierarchyForUser(owner).size());

    Folder child = TestFactory.createAFolder("3", owner);
    Folder gchild = TestFactory.createAFolder("4", owner);
    parent.addChild(child, owner);
    child.addChild(gchild, owner);
    // C->B->A relations
    RSPath trail = gchild.getParentHierarchyForUser(owner);
    assertEquals(3, trail.size());
    assertEquals(parent, trail.getFirstElement().get());
    assertEquals(child, trail.get(1).get());
    assertEquals(gchild, trail.getLastElement().get());

    // C->B->P1,P2

    Folder parent2 = TestFactory.createAFolder("2", user2);
    parent2.addType(RecordType.ROOT);
    user2.setRootFolder(parent2);
    assertNotNull(parent2.addChild(child, user2));
    RSPath trail2 = gchild.getParentHierarchyForUser(user2);
    assertEquals(3, trail2.size());
    assertEquals(parent2, trail2.get(0).get());
    assertEquals(gchild, trail2.get(2).get());

    // edges p1-p2, p1-p3, p3-a, a-b, b-c,c-d; p4-cP,cP-c
    Folder ROOT = createComplexGraph();
    RSPath h1 = d.getParentHierarchyForUser(u4);
    print(h1);
    assertEquals(4, h1.size());

    RSPath h2 = d.getParentHierarchyForUser(user2);
    print(h2);
    assertEquals(5, h2.size());
    RSPath h3 = d.getParentHierarchyForUser(owner);
    print(h3);
    assertEquals(6, h3.size());
    assertEquals(5, d.getParentHierarchyForUser(u3).size());

    // now make a shortcut edge from p3-d:
    p3.addChild(d, p3.getOwner());
    assertEquals(2, d.getParentHierarchyForUser(u3).size());
  }

  @Test
  public void testMove() throws IllegalAddChildOperation {
    ROOT_NODE = createComplexGraph();
    assertTrue(c.move(b, a, owner));
    assertTrue(c.getParentFolders().contains(a));
    assertTrue(a.getChildrens().contains(c));
    assertFalse(c.getParentFolders().contains(b));
    RSPath h3 = d.getParentHierarchyForUser(owner);
    print(h3);

    // can't move into subfolder of oneself
    assertFalse(p1.move(ROOT_NODE, d, owner));
    assertFalse(p1.getParentFolders().contains(b));
    // can't move into oneself
    assertFalse(p1.move(ROOT_NODE, p1, owner));
    // move doesn't succeed if 'from' isnt' a direct parent
    assertFalse(d.move(ROOT_NODE, p2, owner));
    assertTrue(d.hasSingleParent());
    assertEquals(c, d.getSingleParent());
  }

  /**
   * edges p1-p2, p1-p3, p3-a, a-b, b-c,c-d; p4-cP,cP-c
   *
   * @return
   * @throws IllegalAddChildOperation
   */
  static Folder createComplexGraph() throws IllegalAddChildOperation {
    p1 = TestFactory.createAFolder("P1", owner);
    p1.addType(RecordType.ROOT);
    owner.setRootFolder(p1);
    p2 = TestFactory.createAFolder("P2", user2);
    p2.addType(RecordType.ROOT);
    user2.setRootFolder(p2);
    p3 = TestFactory.createAFolder("P3", u3);
    p3.addType(RecordType.ROOT);
    u3.setRootFolder(p3);
    p4 = TestFactory.createAFolder("P4", u4);
    p4.addType(RecordType.ROOT);
    u4.setRootFolder(p4);
    ROOT_NODE = TestFactory.createAFolder("ROOT", owner);

    a = TestFactory.createAFolder("A", owner);
    b = TestFactory.createAFolder("B", owner);
    c = TestFactory.createAFolder("C", owner);
    cP = TestFactory.createAFolder("cP", owner);
    d = TestFactory.createAFolder("D", owner);
    ROOT_NODE.addChild(p4, ROOT);
    p1.addChild(p2, owner);
    p1.addChild(p3, owner);
    p2.addChild(a, u3);
    p3.addChild(a, user2);
    p4.addChild(cP, u4);
    cP.addChild(c, u4);
    c.addChild(d, user2);
    b.addChild(c, user2);
    a.addChild(b, user2);
    return ROOT_NODE;
  }

  static void print(RSPath h1) {
    int lastIndex = h1.size() - 1;
    for (BaseRecord n : h1) {

      System.err.print(n.getName());

      System.err.print("->");
    }
    System.err.println("");
  }
}
