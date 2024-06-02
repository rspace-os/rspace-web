package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.api.v1.model.ApiDocument;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.core.util.DateUtil;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.http.MockHttpOutputMessage;

public class SuperCSVMessageConverterTest {

  SuperCSVMessageConverter csvConverter;

  @Before
  public void setUp() throws Exception {
    csvConverter = new SuperCSVMessageConverter();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testSupportsApiDocument() {
    assertTrue(csvConverter.supports(ApiDocument.class));
  }

  @Test
  public void testWriteInternalObjectHttpOutputMessage()
      throws HttpMessageNotWritableException, IOException, ParseException {
    ApiUser user = API_ModelTestUtils.createAUser("anyuser");
    final int NUM_FIELDS = 2;
    ApiDocument doc = API_ModelTestUtils.createAnyApiDocWithNFields(NUM_FIELDS, user);
    MockHttpOutputMessage message = new MockHttpOutputMessage();
    csvConverter.writeInternal(doc, message);
    String csvResponse = message.getBodyAsString();
    String[] lines = API_ModelTestUtils.parseCSVResponseToLines(csvResponse);

    assertEquals(NUM_FIELDS + 1, lines.length); // header row as well
    final int FIELD_PROPERTY_COUNT = 6;
    API_ModelTestUtils.assertRowAndColumnCount(csvResponse, NUM_FIELDS + 1, FIELD_PROPERTY_COUNT);
    Date date = new Date(DateUtil.convertISO8601ToMillis(lines[1].split(",")[4]));
    assertNotNull(date);
  }
}
