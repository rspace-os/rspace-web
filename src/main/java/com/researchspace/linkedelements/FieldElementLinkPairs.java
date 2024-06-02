package com.researchspace.linkedelements;

import com.researchspace.model.IFieldLinkableElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Value;

@Value
public class FieldElementLinkPairs<T extends IFieldLinkableElement> {

  List<FieldElementLinkPair<T>> pairs = new ArrayList<>();
  private Class<T> genericType;

  public FieldElementLinkPairs(Class<T> genericType) {
    this.genericType = genericType;
  }

  public List<T> getElements() {
    return pairs.stream().map(FieldElementLinkPair::getElement).collect(Collectors.toList());
  }

  public List<String> getLinks() {
    return pairs.stream().map(FieldElementLinkPair::getLink).collect(Collectors.toList());
  }

  public int size() {
    return pairs.size();
  }

  public boolean add(FieldElementLinkPair<T> fieldElementLinkPair) {
    return pairs.add(fieldElementLinkPair);
  }

  public boolean addAll(Collection<? extends FieldElementLinkPair<T>> addedItems) {
    return pairs.addAll(addedItems);
  }

  public boolean supportsClass(Class<?> clazz) {
    return genericType.isAssignableFrom(clazz);
  }
}
