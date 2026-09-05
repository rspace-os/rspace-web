package com.researchspace.linkedelements;

import static org.apache.commons.io.FileUtils.readFileToString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.IFieldLinkableElement;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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

  private @InjectMocks FieldParserImpl fieldParser;
  private @Mock FieldConverterFactory converterFactory;
  private @Mock FieldElementConverter converter;

  @BeforeEach
  public void setUp() throws Exception {
    fieldParser.setElementSelectorFactory(new ElementSelectorFactory());
  }

  @Test
  public void testGetElementsfromEmptyField() {
    FieldContents fieldContents = fieldParser.findFieldElementsInContent("");
    assertNotNull(fieldContents);
    assertNoElements(fieldContents);
    Mockito.verifyNoInteractions(converter, converterFactory);
  }

  @Test
  public void testGetElementsfromFieldWithNoElements() {
    FieldContents fieldContents =
        fieldParser.findFieldElementsInContent("Text field with out elements");

    assertNotNull(fieldContents);
    assertNoElements(fieldContents);
    Mockito.verifyNoInteractions(converter, converterFactory);
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
