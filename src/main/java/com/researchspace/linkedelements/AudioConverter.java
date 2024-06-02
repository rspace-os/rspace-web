package com.researchspace.linkedelements;

import com.researchspace.dao.EcatAudioDao;
import com.researchspace.model.EcatAudio;
import java.util.Optional;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;

class AudioConverter extends AbstractFieldElementConverter implements FieldElementConverter {
  private @Autowired EcatAudioDao ecatAudioDao;

  public Optional<EcatAudio> jsoup2LinkableElement(FieldContents contents, Element el) {
    Optional<EcatAudio> rc = Optional.empty();
    long id = getElementIdFromComposition(el);
    Long revision = getRevisionFromElem(el);
    el = el.parent().getElementById("attachOnText_" + id);
    if (id != -1) {
      rc = getItem(id, revision, EcatAudio.class, ecatAudioDao);
      if (rc.isPresent()) {
        contents.addElement(rc.get(), el.attr("href"), EcatAudio.class);
      } else {
        logNotFound("Audio", id);
      }
    }
    return rc;
  }
}
