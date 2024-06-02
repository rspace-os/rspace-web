package com.researchspace.linkedelements;

import static org.apache.commons.io.FileUtils.readFileToString;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import com.researchspace.model.IFieldLinkableElement;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class FieldParserTest {

  // contains examples of linked elements from VelocityTemplates
  private static File EXAMPLE_LINK_FILE =
      new File("src/test/resources/TestResources/exampleLinks.txt");

  private static String FIELDDATA;

  static {
    try {
      FIELDDATA = readFileToString(EXAMPLE_LINK_FILE, StandardCharsets.UTF_8);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Rule public MockitoRule mockery = MockitoJUnit.rule();

  private @InjectMocks FieldParserImpl fieldParser;
  private @Mock FieldConverterFactory converterFactory;
  private @Mock FieldElementConverter converter;

  @Before
  public void setUp() throws Exception {
    fieldParser.setElementSelectorFactory(new ElementSelectorFactory());
  }

  @Test
  public void testGetElementsfromEmptyField() {
    when(converterFactory.getConverterForClass(Mockito.anyString())).thenReturn(converter);
    FieldContents fieldContents = fieldParser.findFieldElementsInContent("");
    assertNotNull(fieldContents);
    assertNoElements(fieldContents);
    Mockito.verifyZeroInteractions(converter, converterFactory);
  }

  @Test
  public void testGetElementsfromFieldWithNoElements() {
    when(converterFactory.getConverterForClass(Mockito.anyString())).thenReturn(converter);
    FieldContents fieldContents =
        fieldParser.findFieldElementsInContent("Text field with out elements");

    assertNotNull(fieldContents);
    assertNoElements(fieldContents);
    Mockito.verifyZeroInteractions(converter, converterFactory);
  }

  private void assertNoElements(FieldContents fieldContents) {

    assertEquals(0, fieldContents.getSketches().size());
    assertEquals(false, fieldContents.hasSketches());

    assertEquals(0, fieldContents.getImageAnnotations().size());
    assertEquals(false, fieldContents.hasImageAnnotations());

    for (Class<? extends IFieldLinkableElement> clazz : FieldContents.FIELD_ELEMENT_CLASSES) {
      assertEquals(0, fieldContents.getElements(clazz).size());
      assertEquals(false, fieldContents.hasElements(clazz));
    }
  }

  @Test
  public void testHasChemElement() {
    assertTrue(fieldParser.hasChemElement(1L, FIELDDATA));
    assertFalse(fieldParser.hasChemElement(2L, FIELDDATA));
    assertFalse(fieldParser.hasChemElement(1L, "<img class=\"other\""));
  }

  @Test
  public void testGetClasslessImages() {
    String html =
        "<img class='x' src = 'y'/> <img class='imageDropped'/> <img src='z'/> <a href='link'/>";
    assertEquals(2, fieldParser.getNonRSpaceImages(html).size());
  }
}
