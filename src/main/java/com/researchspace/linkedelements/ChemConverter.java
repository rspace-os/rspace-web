package com.researchspace.linkedelements;

import com.researchspace.dao.EcatChemistryFileDao;
import com.researchspace.dao.RSChemElementDao;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.RSChemElement;
import java.util.Optional;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;

class ChemConverter extends AbstractFieldElementConverter implements FieldElementConverter {

  private static final String DATA_CHEM_FILE_ID = "data-chemfileid";

  @Autowired private RSChemElementDao rsChemElementDao;

  @Autowired private EcatChemistryFileDao chemistryFileDao;

  public Optional<RSChemElement> jsoup2LinkableElement(FieldContents contents, Element el) {
    long id = Long.parseLong(el.attr("id"));
    Optional<RSChemElement> rsChemElement =
        getItem(id, null, RSChemElement.class, rsChemElementDao);
    if (rsChemElement.isPresent()) {
      contents.addElement(rsChemElement.get(), el.attr("src"), RSChemElement.class);
      if (el.hasAttr(DATA_CHEM_FILE_ID) && !(el.attr(DATA_CHEM_FILE_ID).isEmpty())) {
        addEcatChemFileIfPresent(contents, el);
      }
    } else {
      logNotFound("Chemical element ", id);
      if (el.hasAttr(DATA_CHEM_FILE_ID) && !(el.attr(DATA_CHEM_FILE_ID).isEmpty())) {
        addEcatChemFileIfPresent(contents, el);
      }
    }
    return rsChemElement;
  }

  private void addEcatChemFileIfPresent(FieldContents contents, Element element) {
    long chemFileId = Long.parseLong(element.attr(DATA_CHEM_FILE_ID));
    Long revision = getRevisionFromElem(element);
    Optional<EcatChemistryFile> ecatChemistryFile =
        getItem(chemFileId, revision, EcatChemistryFile.class, chemistryFileDao);
    if (ecatChemistryFile.isPresent()) {
      contents.addChemistryFile(ecatChemistryFile.get(), element.attr("href"));
    } else {
      logNotFound("Chemistry File ", chemFileId);
    }
  }
}
