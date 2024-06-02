package com.researchspace.export.pdf;

import com.researchspace.archive.ArchivalNfsFile;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import lombok.Getter;

/** Representation of StructuredDocument as HTML and view objects for export or preview. */
@Getter
public class ExportProcesserInput {

  private String documentAsHtml = null;

  private List<CommentAppendix> comments = new ArrayList<>();
  private List<ArchivalNfsFile> nfsLinks = new ArrayList<>();
  private RevisionInfo revisionInfo = null;

  public ExportProcesserInput(
      @Nonnull String documentAsHtml,
      List<CommentAppendix> comments,
      RevisionInfo revisionInfo,
      List<ArchivalNfsFile> nfsLinks) {
    super();
    this.documentAsHtml = documentAsHtml;
    this.comments = comments;
    this.revisionInfo = revisionInfo;
    this.nfsLinks = nfsLinks;
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
