package com.researchspace.linkedelements;

import com.researchspace.dao.EcatVideoDao;
import com.researchspace.model.EcatVideo;
import java.util.Optional;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;

class VideoConverter extends AbstractFieldElementConverter implements FieldElementConverter {

  private @Autowired EcatVideoDao ecatVideoDao;

  public Optional<EcatVideo> jsoup2LinkableElement(FieldContents contents, Element el) {
    long id = getElementIdFromComposition(el);
    Long revision = getRevisionFromElem(el);
    Optional<EcatVideo> ecatVideo = Optional.empty();
    if (id != -1) {
      ecatVideo = getItem(id, revision, EcatVideo.class, ecatVideoDao);
      if (ecatVideo.isPresent()) {
        contents.addElement(ecatVideo.get(), el.attr("data-video"), EcatVideo.class);
      } else {
        logNotFound("Video", id);
      }
    }
    return ecatVideo;
  }
}
