package com.researchspace.linkedelements;

import static com.researchspace.core.util.FieldParserConstants.ATTACHMENT_CLASSNAME;
import static com.researchspace.core.util.FieldParserConstants.AUDIO_CLASSNAME;
import static com.researchspace.core.util.FieldParserConstants.CHEM_IMG_CLASSNAME;
import static com.researchspace.core.util.FieldParserConstants.COMMENT_CLASS_NAME;
import static com.researchspace.core.util.FieldParserConstants.DATA_MATHID;
import static com.researchspace.core.util.FieldParserConstants.EXTERNALFILESTORELINK_KEY;
import static com.researchspace.core.util.FieldParserConstants.IMAGE_DROPPED_CLASS_NAME;
import static com.researchspace.core.util.FieldParserConstants.LINKEDRECORD_CLASS_NAME;
import static com.researchspace.core.util.FieldParserConstants.MATH_CLASSNAME;
import static com.researchspace.core.util.FieldParserConstants.SKETCH_IMG_CLASSNAME;
import static com.researchspace.core.util.FieldParserConstants.VIDEO_CLASSNAME;

import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.core.util.TransformerUtils;
import java.util.HashMap;
import java.util.Map;
import lombok.Value;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

class ElementSelectorFactory {

  Map<String, ElementSelector> cssToSelector = new HashMap<>();

  @Value
  static class BasicCSSSelector implements ElementSelector {
    private String selector;

    @Override
    public Elements select(Element doc) {
      return doc.select("." + selector.toLowerCase());
    }
  }

  ElementSelector chemSelector = doc -> doc.select("." + CHEM_IMG_CLASSNAME).select("[id]");
  ElementSelector mathSelector =
      doc -> doc.select("." + MATH_CLASSNAME).select("[" + DATA_MATHID + "]");
  ElementSelector internalAttachmentSelector =
      doc ->
          doc.select("." + ATTACHMENT_CLASSNAME)
              .select(":not([" + EXTERNALFILESTORELINK_KEY + "])");

  ElementSelectorFactory() {
    initCssSelectors();
  }

  private void initCssSelectors() {
    for (String cssClass :
        TransformerUtils.toList(
            AUDIO_CLASSNAME,
            VIDEO_CLASSNAME,
            LINKEDRECORD_CLASS_NAME,
            SKETCH_IMG_CLASSNAME,
            COMMENT_CLASS_NAME,
            IMAGE_DROPPED_CLASS_NAME,
            FieldParserConstants.NET_FS_CLASSNAME)) {
      cssToSelector.put(cssClass, new BasicCSSSelector(cssClass));
    }
    cssToSelector.put(CHEM_IMG_CLASSNAME, chemSelector);
    cssToSelector.put(MATH_CLASSNAME, mathSelector);
    cssToSelector.put(ATTACHMENT_CLASSNAME, internalAttachmentSelector);
  }

  ElementSelector getElementSelectorForClass(String cssClass) {
    return cssToSelector.get(cssClass);
  }
}
