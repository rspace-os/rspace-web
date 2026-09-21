package com.researchspace.model.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.researchspace.model.User;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CycleSafeIteratorTest {

  User u = TestFactory.createAnyUser("any");

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void test() throws InterruptedException, IllegalAddChildOperation {

    Folder f1 = TestFactory.createAFolder("f1", u);
    Thread.sleep(1);
    Folder f2 = TestFactory.createAFolder("f2", u);
    Thread.sleep(1);
    Folder f3 = TestFactory.createAFolder("f3", u);
    Thread.sleep(1);
    Folder f4 = TestFactory.createAFolder("f4", u);
    Thread.sleep(1);
    Folder f5 = TestFactory.createAFolder("f5", u);
    Thread.sleep(1);
    Folder f6 = TestFactory.createAFolder("f6", u);
    Thread.sleep(1);
    Folder f7 = TestFactory.createAFolder("f7", u);
    Thread.sleep(1);
    CycleSafeIterator it = new CycleSafeIterator(f3);
    // check isolated node returns false
    assertFalse(it.hasNext());
    assertThrows(NoSuchElementException.class, it::next);
    // f1->f2->f3,f1->f4
    f1.doAddToParentsOnly(f2, u);
    f1.doAddToParentsOnly(f4, u);
    f2.doAddToParentsOnly(f3, u);

    it = new CycleSafeIterator(f3);
    assertNElementsIterated(2, it);

    f4.doAddToParentsOnly(f3, u);
    it = new CycleSafeIterator(f3);
    assertNElementsIterated(3, it);

    // redundant edge, f3 already reachable to f1 via f2
    f1.doAddToParentsOnly(f3, u);
    it = new CycleSafeIterator(f3);
    assertNElementsIterated(3, it);

    f5.addChild(f1, u, true);
    it = new CycleSafeIterator(f3);
    assertNElementsIterated(4, it);
    it = new CycleSafeIterator(f4);
    assertNElementsIterated(2, it);

    // forcibly create cycle by calling method which does not check for cycles.
    f7.doAddToParentsOnly(f1, u);
    f3.doAddToParentsOnly(f7, u);
    boolean addexception = false;
    try {
      f1.addChild(f6, u, true);
    } catch (IllegalAddChildOperation e) {
      addexception = true;
    }
    if (!addexception) {
      fail();
    }
    CycleSafeIterator it2 = new CycleSafeIterator(f1);
    while (it2.hasNext()) {
      BaseRecord br = it2.next();
    }
    assertTrue(it2.isCycleDetected());
  }

  @Test
  public void deepChainVisitsEveryAncestorOnce() throws IllegalAddChildOperation {
    final int NUM_FOLDERS = 60;
    Folder[] flders = new Folder[NUM_FOLDERS];
    flders[0] = TestFactory.createAFolder("0", u);
    flders[0].setId(1L);
    for (int i = 1; i < NUM_FOLDERS; i++) {
      flders[i] = TestFactory.createAFolder(i + "", u);
      flders[i].setId((long) i + 1);
      flders[i - 1].addChild(flders[i], u, true);
    }
    CycleSafeIterator cycleIt = new CycleSafeIterator(flders[NUM_FOLDERS - 1]);
    Set<Long> visited = new HashSet<>();
    while (cycleIt.hasNext()) {
      assertTrue(visited.add(cycleIt.next().getId()), "Ancestor visited twice");
    }
    assertEquals(LongStream.range(1, NUM_FOLDERS).boxed().collect(Collectors.toSet()), visited);
    assertFalse(cycleIt.isCycleDetected());
  }

  private void assertNElementsIterated(int target, CycleSafeIterator it) {
    int count = 0;
    while (it.hasNext()) {
      BaseRecord br = it.next();
      count++;
    }
    assertEquals(target, count);
  }

  @Test
  public void testRemoeOperationUnsupported() {
    Folder f1 = TestFactory.createAFolder("f1", u);
    Iterator<Folder> cycleIt = new CycleSafeIterator(f1);

    assertThrows(UnsupportedOperationException.class, () -> cycleIt.remove());
  }
}
