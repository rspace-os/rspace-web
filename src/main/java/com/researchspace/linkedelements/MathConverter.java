package com.researchspace.linkedelements;

import static com.researchspace.core.util.FieldParserConstants.DATA_MATHID;

import com.researchspace.dao.RSMathDao;
import com.researchspace.model.RSMath;
import java.util.Optional;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;

class MathConverter extends AbstractFieldElementConverter implements FieldElementConverter {
  private @Autowired RSMathDao mathDao;

  public Optional<RSMath> jsoup2LinkableElement(FieldContents contents, Element el) {
    long id = Long.parseLong(el.attr(DATA_MATHID));
    Optional<RSMath> rsMathElement = getItem(id, null, RSMath.class, mathDao);
    if (rsMathElement.isPresent()) {
      String url = el.getElementsByTag("object").attr("data");
      contents.addElement(rsMathElement.get(), url, RSMath.class);
    } else {
      logNotFound("Math element", id);
    }
    return rsMathElement;
  }
}
