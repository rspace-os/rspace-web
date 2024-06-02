package com.researchspace.service.archive.export;

import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.model.EcatMediaFile;

class TestFieldExporter extends AbstractFieldExporter<EcatMediaFile> {

  @Override
  void createFieldArchiveObject(EcatMediaFile item, String newLink, FieldExportContext context) {}

  @Override
  String doUpdateLinkText(
      FieldElementLinkPair<EcatMediaFile> itemPair,
      String replacementUrl,
      FieldExportContext context) {
    return "";
  }

  @Override
  String getReplacementUrl(FieldExportContext context, EcatMediaFile item) {
    return null;
  }
}
