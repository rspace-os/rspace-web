package com.researchspace.webapp.controller;

import com.researchspace.model.field.ErrorList;
import org.junit.Test;

public class AjaxReturnObjectTest {

  @Test
  public void testAjaxReturnObjectNullErrorOK() {
    new AjaxReturnObject<String>("data", null);
  }

  @Test
  public void testAjaxReturnObjectNullDataOK() {
    ErrorList el = new ErrorList();
    new AjaxReturnObject<String>(null, el);
  }
}
