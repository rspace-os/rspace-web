package com.researchspace.linkedelements;

public interface FieldConverterFactory {

  FieldElementConverter getConverterForClass(String cssClass);
}
