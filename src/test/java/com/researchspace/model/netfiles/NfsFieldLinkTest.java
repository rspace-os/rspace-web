package com.researchspace.model.netfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.Test;

class NfsFieldLinkTest {

  @Test
  void absolutePathCombinesFilestoreBaseAndLinkPathKeepingInnerSlashes() {
    NfsFileStore store =
        GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(1L, "s3", new User("u"));
    store.setPath("/dev-folder/");
    Field field = TestFactory.createTextFieldForm().createNewFieldFromForm();

    NfsFieldLink link = new NfsFieldLink(field, store, "/XRD//run.dat/");

    // S3 keys are flat, so an inner doubled slash names a different object
    assertEquals("dev-folder/XRD//run.dat", link.getAbsolutePath());
    assertEquals("/XRD//run.dat/", link.getRelPath());
    assertSame(store.getFileSystem(), link.getFileSystem());
  }

  @Test
  void normalisePathStripsOnlyBoundarySlashes() {
    assertEquals("", NfsFieldLink.normalisePath("/"));
    assertEquals("a/b", NfsFieldLink.normalisePath("//a/b/"));
    assertEquals("a//b", NfsFieldLink.normalisePath("/a//b"));
  }
}
