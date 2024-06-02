package com.researchspace.linkedelements;

import com.researchspace.model.IFieldLinkableElement;
import java.util.Optional;
import org.jsoup.nodes.Element;

/**
 * Converter for Text field elements represented as JSoup elements to {@link IFieldLinkableElement}
 * subclasses
 */
public interface FieldElementConverter {

  /**
   * Gets the persistent entity represented in a text field by a JSoupd element. Implementations
   * shold add a successfully retrieved IFieldLinkableElement to the supplied {@link FieldContents}
   * object
   *
   * @param contents
   * @param el
   * @return An {@link Optional}IFieldLinkableElement
   */
  Optional<? extends IFieldLinkableElement> jsoup2LinkableElement(
      FieldContents contents, Element el);
}
