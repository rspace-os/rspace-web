package com.researchspace.service;

import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSPath;
import com.researchspace.testutils.TestFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DeletionPlanTest {
  DeletionPlan plan = null;
  User any = null;
  Folder parentFolder, toDelete, child;
  List<BaseRecord> pathElements = new ArrayList<>();

  @BeforeEach
  public void setUp() throws Exception {
    any = createAnyUser("any");
    parentFolder = TestFactory.createAFolder("parent", any);
    toDelete = TestFactory.createAFolder("toDelete", any);
    child = TestFactory.createAFolder("child", any);
    parentFolder.addChild(toDelete, any);
    toDelete.addChild(child, any);
    pathElements = Arrays.asList(new BaseRecord[] {parentFolder, toDelete, child});
  }

  @Test
  public void testDeletionPlan() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          plan = new DeletionPlan(any, null, parentFolder);
        });
  }

  @Test
  public void testAdd() {
    RSPath path = new RSPath(pathElements);
    plan = new DeletionPlan(any, path, parentFolder);
    plan.add(toDelete);
    plan.add(child);
    assertEquals(toDelete, plan.getFinalElementToRemove());
  }

  @Test
  public void testGetParentOfDeletedItem() {
    RSPath path = new RSPath(pathElements);
    plan = new DeletionPlan(any, path, parentFolder);
    plan.add(toDelete);
    plan.add(child);
    assertEquals(parentFolder, plan.getParentOfDeletedItem());
    assertEquals(child, plan.iterator().next());
  }
}
