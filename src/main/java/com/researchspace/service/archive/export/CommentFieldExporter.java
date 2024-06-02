package com.researchspace.service.archive.export;

import static com.researchspace.core.util.FieldParserConstants.COMMENT_ICON_URL;

import com.researchspace.archive.ArchivalField;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.service.archive.ExportImport;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class CommentFieldExporter extends AbstractFieldExporter<EcatComment> {

  CommentFieldExporter(FieldExporterSupport support) {
    super(support);
  }

  CommentFieldExporter() {}

  void createFieldArchiveObject(EcatComment item, String newLink, FieldExportContext context) {
    ArchivalField archiveField = context.getArchiveField();
    if (context.getRevision() != null) {
      Integer itg = Integer.valueOf(context.getRevision().intValue());
      List<EcatCommentItem> items =
          support
              .getAuditManager()
              .getCommentItemsForCommentAtDocumentRevision(item.getComId(), itg);
      if (items != null && items.size() > 0) {
        EcatComment comment = new EcatComment();
        comment.setItems(items);
        comment.setAuthor(item.getAuthor());
        comment.setLastUpdater(item.getLastUpdater());
        comment.setUpdateDate(item.getUpdateDate());
        archiveField.addArchivalComment(archiveModelFactory.createComment(comment));
      }
    } else {
      archiveField.addArchivalComment(archiveModelFactory.createComment(item));
    }
  }

  String doUpdateLinkText(
      FieldElementLinkPair<EcatComment> mediaFilePair,
      String archiveLink,
      FieldExportContext context) {
    return support
        .getRichTextUpdater()
        .replaceImageSrc(COMMENT_ICON_URL, archiveLink, context.getArchiveField().getFieldData());
  }

  @Override
  String getReplacementUrl(FieldExportContext context, EcatComment item)
      throws URISyntaxException, IOException {
    Optional<String> resourceFileNameOpt =
        support
            .getResourceCopier()
            .copyFromClassPathResourceToArchiveResources(
                COMMENT_ICON_URL, context.getRecordFolder().getParentFile());
    if (resourceFileNameOpt.isPresent()) {
      return "../" + ExportImport.RESOURCES + "/" + resourceFileNameOpt.get();

    } else {
      log.warn("Resource file for comment icon not present");
      return COMMENT_ICON_URL; // unchanged
    }
  }
}
