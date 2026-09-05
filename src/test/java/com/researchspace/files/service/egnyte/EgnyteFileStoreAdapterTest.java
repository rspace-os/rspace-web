package com.researchspace.files.service.egnyte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class EgnyteFileStoreAdapterTest {

  EgnyteFileStoreAdapter adapter = null;

  @Test
  public void testEgnyteFileStoreAdapter() throws Exception {
    adapter = new EgnyteFileStoreAdapter("http://base.url/", "folder/path");
    assertEquals("folder/path/", adapter.getFileStoreRoot());
    adapter = new EgnyteFileStoreAdapter("http://base.url/", "folder2/path/");
    assertEquals("folder2/path/", adapter.getFileStoreRoot());

    assertThrows(
        IllegalArgumentException.class,
        () -> new EgnyteFileStoreAdapter("Not a url!", "folder2/path/"));
  }
}
