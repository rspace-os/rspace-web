package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RestoreDeletedItemResultTest {
  User u;
  StructuredDocument sd1;
  Folder folder;

  @BeforeEach
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

    assertThrows(UnsupportedOperationException.class, () -> result.getRestoredItems().add(sd1));
  }
}
