package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.*;

import com.researchspace.api.v1.model.ApiSearchTerm.QueryTypeEnum;
import org.junit.jupiter.api.Test;

class ApiSearchTermTest {

  @Test
  void testUpdateTermToISO8601IfDateTerm() {
    ApiSearchTerm term = new ApiSearchTerm(";2014-01-01", QueryTypeEnum.CREATED);
    term.updateTermToISO8601IfDateTerm();
    assertEquals(";2014-01-01T23:59:59Z", term.getQuery());

    term = new ApiSearchTerm("2014-01-01;", QueryTypeEnum.LASTMODIFIED);
    term.updateTermToISO8601IfDateTerm();
    assertEquals("2014-01-01T00:00Z;", term.getQuery());

    term = new ApiSearchTerm("2014-01-01;2015-01-01", QueryTypeEnum.LASTMODIFIED);
    term.updateTermToISO8601IfDateTerm();
    assertEquals("2014-01-01T00:00Z;2015-01-01T23:59:59Z", term.getQuery());

    term = new ApiSearchTerm("2014-01-01T00:00+0200;2015-01-01", QueryTypeEnum.LASTMODIFIED);
    term.updateTermToISO8601IfDateTerm();
    assertEquals("2014-01-01T00:00+0200;2015-01-01T23:59:59Z", term.getQuery());

    // only dates are modified
    term = new ApiSearchTerm("2014-01-01;", QueryTypeEnum.TAG);
    term.updateTermToISO8601IfDateTerm();
    assertEquals("2014-01-01;", term.getQuery());

    // iso8601  is not altered
    term = new ApiSearchTerm(";2014-02-02T12:34:56Z", QueryTypeEnum.LASTMODIFIED);
    term.updateTermToISO8601IfDateTerm();
    assertEquals(";2014-02-02T12:34:56Z", term.getQuery());
  }
}
