package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.model.EcatImage;
import java.io.IOException;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@Slf4j
public class ImageFieldExporter extends AbstractFieldExporter<EcatImage> {

  public static Document addImageAltToImages(Document doc) {
    Elements images = doc.getElementsByTag("img");
    for (Element el : images) {
      if (el.hasAttr("alt")) {
        String imageName = el.attr("alt");
        if (!imageName.isEmpty()) {
          el.before("<p>" + imageName + ": </p>");
        }
      }
    }
    return doc;
  }

  public static Document resizeChemImages(Document doc) {
    doc.select("img.chem")
        .forEach(
            el -> {
              el.attr("width", "90%");
              el.attr("height", "90%");
            });

    return doc;
  }

  ImageFieldExporter(FieldExporterSupport support) {
    super(support);
  }

  ImageFieldExporter() {}

  void createFieldArchiveObject(EcatImage item, String archiveLink, FieldExportContext context) {
    ArchivalField field = context.getArchiveField();
    ArchivalGalleryMetadata agm = archiveModelFactory.createGalleryMetadata(item);
    agm.setParentId(field.getFieldId());
    agm.setLinkFile(archiveLink);

    // if image is a tiff, then export original file along the working copy
    String originalLink = new RelativeLinkProcessor(context, support).getOriginalFile(item);
    if (!StringUtils.isEmpty(originalLink)) {
      agm.setLinkToOriginalFile(originalLink);
    }
    field.addArchivalImageMetadata(agm);
  }

  String doUpdateLinkText(
      FieldElementLinkPair<EcatImage> itemPair, String replacementUrl, FieldExportContext context) {
    ArchivalField field = context.getArchiveField();
    return support
        .getRichTextUpdater()
        .replaceImageSrcURL(
            field.getFieldId() + "-" + itemPair.getElement().getId(),
            field.getFieldData(),
            replacementUrl);
  }

  @Override
  String getReplacementUrl(FieldExportContext context, EcatImage item)
      throws URISyntaxException, IOException {
    return new RelativeLinkProcessor(context, support)
        .getLinkReplacement(item)
        .getRelativeLinkToReplaceLinkInText();
  }
}
