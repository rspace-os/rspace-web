package com.researchspace.webapp.controller;

import java.util.ArrayList;
import java.util.List;

/*
 * Holds common methods for StructuredDocument and Template classes.
 */
public class SDocHelper {

  public static List<String> popoulateFieldTypeList() {
    List<String> fieldtype = new ArrayList<String>();
    fieldtype.add("Number");
    fieldtype.add("String");
    fieldtype.add("Text");
    fieldtype.add("Radio");
    fieldtype.add("Choice");
    fieldtype.add("Date");
    fieldtype.add("Time");
    return fieldtype;
  }
}
