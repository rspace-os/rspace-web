package com.researchspace.linkedelements;

import java.util.HashMap;
import java.util.Map;

public class FieldParserFactoryImpl implements FieldConverterFactory {
  Map<String, FieldElementConverter> cssToParser = new HashMap<>();

  FieldParserFactoryImpl() {}

  public FieldElementConverter getConverterForClass(String cssClass) {
    return cssToParser.get(cssClass);
  }

  void setConverters(Map<String, FieldElementConverter> cssToParser) {
    this.cssToParser = cssToParser;
  }
}
