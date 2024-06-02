package com.researchspace.archive;

import java.util.Comparator;
import lombok.Getter;
import lombok.Setter;

/**
 * Wrapper object to hold the references of unmarshalled XML StructuredDocument object and
 * associated files.
 */
@Getter
@Setter
public class ArchivalDocumentParserRef extends AbstractArchivalParserRef {

  ArchivalDocument archivalDocument;
  ArchivalForm archivalForm;

  /** Sorts by creation date Asc of underlying ArchivalDocument */
  public static final Comparator<ArchivalDocumentParserRef>
      SortArchivalDocumentParserRefByCreationDateAsc =
          (o1, o2) ->
              o1.getArchivalDocument()
                  .getCreationDate()
                  .compareTo(o2.getArchivalDocument().getCreationDate());

  public boolean isDocument() {
    return true;
  }

  @Override
  public long getRevision() {
    return getArchivalDocument().getVersion();
  }
}
