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
  void absolutePathCombinesFilestoreBaseAndLinkPathNormalised() {
    NfsFileStore store =
        GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(1L, "s3", new User("u"));
    store.setPath("/dev-folder/");
    Field field = TestFactory.createTextFieldForm().createNewFieldFromForm();

    NfsFieldLink link = new NfsFieldLink(field, store, "/XRD//run.dat/");

    assertEquals("dev-folder/XRD/run.dat", link.getAbsolutePath());
    assertEquals("/XRD//run.dat/", link.getRelPath());
    assertSame(store.getFileSystem(), link.getFileSystem());
  }

  @Test
  void normalisePathTreatsRootAndNullAsEmpty() {
    assertEquals("", NfsFieldLink.normalisePath(null));
    assertEquals("", NfsFieldLink.normalisePath("/"));
    assertEquals("a/b", NfsFieldLink.normalisePath("//a/b/"));
  }
}
