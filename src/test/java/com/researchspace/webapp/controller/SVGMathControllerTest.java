package com.researchspace.webapp.controller;

import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import com.researchspace.model.RSMath;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.MediaManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserManager;
import java.io.IOException;
import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.springframework.context.support.StaticMessageSource;

@RunWith(MockitoJUnitRunner.class)
public class SVGMathControllerTest {
  private static final long ANY_MATH_ELEMENT_ID = 2L;

  private static final long ANY_FIELD_ID = 1L;

  private static final String ERRORS_REQUIRED = "errors.required";

  private static final String VALIDXML_BUT_WRONG_NAMESPACE =
      "<element xmlns:x=\"http://some.namespace.com\"/>";

  private static final String VALID_SVG = "<svg  xmlns=\"http://www.w3.org/2000/svg\" />";

  private static final String VALID_LATEX = "x^2";

  public MockitoRule mockito = MockitoJUnit.rule();

  @Mock MediaManager mediaMgr;
  @Mock UserManager userManager;
  @InjectMocks SVGMathController svg;
  private StaticMessageSource mockMessageSource;

  @Before
  public void setUp() throws Exception {
    mockMessageSource = new StaticMessageSource();
    svg.setMessageSource(new MessageSourceUtils(mockMessageSource));
  }

  @After
  public void tearDown() throws Exception {}

  @Test(expected = IllegalArgumentException.class)
  public void testSaveSvg2Validate() {
    mockMessageSource.addMessage(ERRORS_REQUIRED, Locale.getDefault(), "need svg");
    svg.saveSvg("", ANY_FIELD_ID, VALID_LATEX, ANY_MATH_ELEMENT_ID);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSaveSvg2ValidateNeedSVG() {
    mockMessageSource.addMessage(ERRORS_REQUIRED, Locale.getDefault(), "need latex");
    svg.saveSvg(VALID_SVG, ANY_FIELD_ID, "", ANY_MATH_ELEMENT_ID);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSaveSvg2ValidatLatexTooLong() {
    mockMessageSource.addMessage("errors.maxlength", Locale.getDefault(), "need latex");
    svg.saveSvg(
        VALID_SVG,
        ANY_FIELD_ID,
        randomAlphabetic(RSMath.LATEX_COLUMN_SIZE + 1),
        ANY_MATH_ELEMENT_ID);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSaveSvg2ValidateSVG() {
    mockMessageSource.addMessage("errors.invalidxml", Locale.getDefault(), "need latex");
    svg.saveSvg("not xml", ANY_FIELD_ID, VALID_LATEX, ANY_MATH_ELEMENT_ID);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSaveSvg2ValidateSVGNamespace() {
    mockMessageSource.addMessage("errors.invalidxml.ns", Locale.getDefault(), "need latex");
    svg.saveSvg(VALIDXML_BUT_WRONG_NAMESPACE, ANY_FIELD_ID, VALID_LATEX, ANY_MATH_ELEMENT_ID);
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
