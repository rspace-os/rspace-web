package com.researchspace.files.service.egnyte;

import static org.junit.Assert.assertEquals;

import com.researchspace.core.testutil.CoreTestUtils;
import org.junit.Test;

public class EgnyteFileStoreAdapterTest {

  EgnyteFileStoreAdapter adapter = null;

  @Test
  public void testEgnyteFileStoreAdapter() throws Exception {
    adapter = new EgnyteFileStoreAdapter("http://base.url/", "folder/path");
    assertEquals("folder/path/", adapter.getFileStoreRoot());
    adapter = new EgnyteFileStoreAdapter("http://base.url/", "folder2/path/");
    assertEquals("folder2/path/", adapter.getFileStoreRoot());

    CoreTestUtils.assertExceptionThrown(
        () -> new EgnyteFileStoreAdapter("Not a url!", "folder2/path/"),
        IllegalArgumentException.class);
  }
}
