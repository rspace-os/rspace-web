package com.researchspace.service;

import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;
import static org.junit.Assert.assertEquals;

import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import org.junit.Before;
import org.junit.Test;

public class RestoreDeletedItemResultTest {
  User u;
  StructuredDocument sd1;
  Folder folder;

  @Before
  public void setup() {
    u = TestFactory.createAnyUser("anyuser");
    sd1 = TestFactory.createAnySD();
    sd1.setOwner(u);

    folder = TestFactory.createAFolder("anyfolder", u);
    folder.setOwner(u);
  }

  @Test
  public void testRestoreDeletedItemResult() throws Exception {
    // basic assertion of invariants
    RestoreDeletedItemResult result = new RestoreDeletedItemResult(folder);
    assertEquals(folder, result.getItemToRestore());
    assertEquals(1, result.getRestoredItemCount());
    assertEquals(folder, result.getFirstItem().get());
    assertEquals(folder, result.getRestoredItems().iterator().next());

    result.addItem(sd1);
    assertEquals(2, result.getRestoredItemCount());
    assertEquals(folder, result.getFirstItem().get());

    assertExceptionThrown(
        () -> result.getRestoredItems().add(sd1), UnsupportedOperationException.class);
  }
}
