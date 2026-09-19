package com.researchspace.model.netfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.core.GlobalIdPrefix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NfsElementTest {
  NfsElement element1, element2 = null;

  @BeforeEach
  public void before() {
    element1 = new NfsElement(26L, "/x/y/z/file.txt");
    element2 = new NfsElement(27L, "x/y/z/file.txt");
  }

  @Test
  public void testToString() {
    assertEquals("26:/x/y/z/file.txt", element1.toString());
    assertTrue(NfsElement.ExpectedLinkFormat.matcher(element1.toString()).matches());

    assertEquals("27:x/y/z/file.txt", element2.toString());
    assertTrue(NfsElement.ExpectedLinkFormat.matcher(element2.toString()).matches());
  }

  @Test
  public void testGetFileStoreId() {
    assertNotNull(element1.getId());
    assertEquals(GlobalIdPrefix.NF, element1.getOid().getPrefix());
  }
}
