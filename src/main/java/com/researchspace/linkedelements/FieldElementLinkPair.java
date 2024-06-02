package com.researchspace.linkedelements;

import com.researchspace.model.IFieldLinkableElement;
import lombok.Value;

/**
 * A tuple of a {@link IFieldLinkableElement} and a String link to that element as it is stored in a
 * text field.
 *
 * @param <T> A subclass of IFieldLinkableElement
 */
@Value
public class FieldElementLinkPair<T extends IFieldLinkableElement> {

  private T element;
  private String link;
}
