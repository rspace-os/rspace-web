package com.researchspace.service.archive;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.archive.ArchiveFolder;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.RecordType;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ExportRecordListTest {

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void attachmentGlobalIdsIgnoreVersions() {
    ExportRecordList toExport = new ExportRecordList();
    GlobalIdentifier gidv1 = new GlobalIdentifier("GL1234v1");
    GlobalIdentifier gidv2 = new GlobalIdentifier("GL1234v2");
    toExport.addAllFieldAttachments(toList(gidv1, gidv2));
    // no version
    assertTrue(toExport.containsFieldAttachment(new GlobalIdentifier("GL1234")));
    // matching version
    assertTrue(toExport.containsFieldAttachment(new GlobalIdentifier("GL1234v2")));
    // unmatching version
    assertTrue(toExport.containsFieldAttachment(new GlobalIdentifier("GL1234v6")));
  }

  @Test
  public void testGetTopLevelFolders() {
    // this is structure
    // f1/
    //  f3/f5
    //  f4
    // f6
    // f6 and f1 were exported from folders  7  and 2.
    ArchiveFolder f1 = createFolder(1l, 2l);
    ArchiveFolder f3 = createFolder(3l, 1l);
    ArchiveFolder f4 = createFolder(4l, 1l);
    ArchiveFolder f5 = createFolder(5l, 3l);
    ArchiveFolder f6 = createFolder(6l, 7l);
    List<ArchiveFolder> flders = Arrays.asList(new ArchiveFolder[] {f1, f3, f4, f5, f6});
    ExportRecordList exported = new ExportRecordList();
    exported.getFolderTree().addAll(flders);

    List<ArchiveFolder> topLEvel = exported.getTopLevelFolders();
    assertEquals(2, topLEvel.size());
    assertTrue(topLEvel.contains(f1));
    assertTrue(topLEvel.contains(f6));

    // now test children
    assertEquals(2, exported.getChildren(1L).size());
    assertEquals(f5, exported.getChildren(3L).get(0));
    assertTrue(exported.getChildren(6L).isEmpty());

    // now check isGallery item:
    assertFalse(exported.archiveParentFolderMatches(f1.getId(), isGallery())); // parent is null;
    assertFalse(exported.archiveParentFolderMatches(f4.getId(), isGallery())); // parent is null;

    // now make f3 root media, and f5 a supposed media folder
    f3.setType(RecordType.ROOT_MEDIA.name());
    f5.setType(RecordType.SYSTEM.name());
    assertTrue(exported.archiveParentFolderMatches(f5.getId(), isGallery()));
  }

  Predicate<ArchiveFolder> isGallery() {
    return af -> af.isRootMediaFolder();
  }

  public static ArchiveFolder createFolder(long id, long parentId) {
    ArchiveFolder fler = new ArchiveFolder();
    fler.setId(id);
    fler.setParentId(parentId);
    fler.setName(id + "");
    return fler;
  }
}
