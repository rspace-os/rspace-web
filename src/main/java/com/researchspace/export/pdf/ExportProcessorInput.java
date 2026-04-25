package com.researchspace.export.pdf;

import com.researchspace.archive.ArchivalNfsFile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Representation of StructuredDocument as HTML and view objects for export or preview. */
@Getter
@AllArgsConstructor
public class ExportProcessorInput {

  private String documentAsHtml = null;

  private List<CommentAppendix> comments = new ArrayList<>();
  private RevisionInfo revisionInfo = null;
  private List<ArchivalNfsFile> nfsLinks = new ArrayList<>();
  private Set<String> igsnInventoryLinkedItems = new HashSet<>();

  public boolean hasStoichiometryTable() {
    return documentAsHtml.contains("data-stoichiometry-table");
  }

  public boolean hasComments() {
    return comments != null && comments.size() > 0;
  }

  public boolean hasRevisionInfo() {
    return revisionInfo != null && !revisionInfo.isEmpty();
  }

  public boolean hasNfsLinks() {
    return nfsLinks != null && !nfsLinks.isEmpty();
  }
}
