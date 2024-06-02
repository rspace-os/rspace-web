package com.researchspace.linkedelements;

import com.researchspace.dao.EcatDocumentFileDao;
import com.researchspace.model.EcatDocumentFile;
import java.util.Optional;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;

class AttachmentConverter extends AbstractFieldElementConverter implements FieldElementConverter {

  @Autowired EcatDocumentFileDao ecatDocumentFileDao;

  public Optional<EcatDocumentFile> jsoup2LinkableElement(FieldContents contents, Element el) {
    if (el.hasAttr("id")) {
      String[] ids = el.attr("id").split("_");
      if (ids.length > 1) {
        long id = Long.parseLong(ids[1]);
        Long revision = getRevisionFromElem(el);
        Optional<EcatDocumentFile> ecatDocumentFile =
            getItem(id, revision, EcatDocumentFile.class, ecatDocumentFileDao);
        if (ecatDocumentFile.isPresent()) {
          contents.addElement(ecatDocumentFile.get(), el.attr("href"), EcatDocumentFile.class);
        } else {
          logNotFound("Attachment", id);
        }
        return ecatDocumentFile;
      }
    }
    return Optional.empty();
  }
}
