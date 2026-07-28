package com.researchspace.webapp.controller;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.researchspace.model.RSMath;
import com.researchspace.model.User;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MediaManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SVGMathControllerTest {
  private static final long ANY_MATH_ELEMENT_ID = 2L;

  private static final long ANY_FIELD_ID = 1L;

  private static final String VALIDXML_BUT_WRONG_NAMESPACE =
      "<element xmlns:x=\"http://some.namespace.com\"/>";

  private static final String VALID_SVG = "<svg  xmlns=\"http://www.w3.org/2000/svg\" />";

  private static final String VALID_LATEX = "x^2";

  @Mock MediaManager mediaMgr;
  @Mock UserManager userManager;
  @InjectMocks SVGMathController svg;

  @BeforeEach
  public void setUp() throws Exception {
    svg.setMessageSource(new MessageSourceUtils(new JsonMessageSource()));
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testSaveSvg2Validate() {
    assertThrows(
        IllegalArgumentException.class,
        () -> svg.saveSvg("", ANY_FIELD_ID, VALID_LATEX, ANY_MATH_ELEMENT_ID));
  }

  @Test
  public void testSaveSvg2ValidateNeedSVG() {
    assertThrows(
        IllegalArgumentException.class,
        () -> svg.saveSvg(VALID_SVG, ANY_FIELD_ID, "", ANY_MATH_ELEMENT_ID));
  }

  @Test
  public void testSaveSvg2ValidatLatexTooLong() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            svg.saveSvg(
                VALID_SVG,
                ANY_FIELD_ID,
                randomAlphabetic(RSMath.LATEX_COLUMN_SIZE + 1),
                ANY_MATH_ELEMENT_ID));
  }

  @Test
  public void testSaveSvg2ValidateSVG() {
    assertThrows(
        IllegalArgumentException.class,
        () -> svg.saveSvg("not xml", ANY_FIELD_ID, VALID_LATEX, ANY_MATH_ELEMENT_ID));
  }

  @Test
  public void testSaveSvg2ValidateSVGNamespace() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            svg.saveSvg(
                VALIDXML_BUT_WRONG_NAMESPACE, ANY_FIELD_ID, VALID_LATEX, ANY_MATH_ELEMENT_ID));
  }

  @Test
  public void testSaveSvg2OK() throws IOException {
    User user = TestFactory.createAnyUser("any");
    when(userManager.getAuthenticatedUserInSession()).thenReturn(user);
    RSMath math = TestFactory.createAMathElement();
    math.setId(2L);
    when(mediaMgr.saveMath(VALID_SVG, ANY_FIELD_ID, VALID_LATEX, 2L, user)).thenReturn(math);
    assertNotNull(svg.saveSvg(VALID_SVG, ANY_FIELD_ID, VALID_LATEX, ANY_MATH_ELEMENT_ID));
  }
}
