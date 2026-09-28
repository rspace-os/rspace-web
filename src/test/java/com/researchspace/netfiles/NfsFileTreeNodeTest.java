package com.researchspace.netfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.netfiles.NfsFileStore;
import org.junit.jupiter.api.Test;

public class NfsFileTreeNodeTest {

  @Test
  public void testLogicPathCalculations() {

    NfsFileTreeNode testNode = new NfsFileTreeNode();

    NfsFileStore testUserFolder = new NfsFileStore();
    testUserFolder.setId(1L);
    testUserFolder.setPath("CSE");

    testNode.calculateLogicPath("CSE/test2", testUserFolder);
    assertEquals("1:/test2", testNode.getLogicPath());

    testNode.calculateLogicPath("//CSE/test/test2", testUserFolder);
    assertEquals("1:/test/test2", testNode.getLogicPath());

    testNode.calculateLogicPath("CSE/it/Thumbs.db", testUserFolder);
    assertEquals("1:/it/Thumbs.db", testNode.getLogicPath());
  }

  @Test
  public void fileNameDropsLeadingAndTrailingSlashesOnly() {
    NfsFileTreeNode testNode = new NfsFileTreeNode();
    testNode.calculateFileName("///dir/file.txt//");
    assertEquals("dir/file.txt", testNode.getFileName());
    testNode.calculateFileName("plain");
    assertEquals("plain", testNode.getFileName());
    testNode.calculateFileName("////");
    assertEquals("", testNode.getFileName());
  }
}
