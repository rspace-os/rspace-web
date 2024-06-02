package com.researchspace.linkedelements;

import com.researchspace.dao.AuditDao;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.VelocityTestUtils;
import org.apache.velocity.app.VelocityEngine;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public abstract class AbstractParserTest {

  protected @Mock AuditDao auditDao;

  @Rule public MockitoRule mockery = MockitoJUnit.rule();

  protected RichTextUpdater rtu;
  protected ElementSelectorFactory selectorFactory;

  protected User anyUser;
  protected FieldContents contents;
  protected VelocityEngine velocity;

  @Before
  public void setUp() throws Exception {
    anyUser = TestFactory.createAnyUser("any");
    contents = new FieldContents();
    selectorFactory = new ElementSelectorFactory();
    rtu = new RichTextUpdater();
    setUpVelocity();
  }

  private void setUpVelocity() {
    velocity =
        VelocityTestUtils.setupVelocity("src/main/resources/velocityTemplates/textFieldElements");
    rtu.setVelocity(velocity);
  }

  Element getElementToConvert(String elementHTml, String cssClassname) {
    Element toParse = Jsoup.parse(elementHTml, "", Parser.xmlParser());
    Element toconvert =
        selectorFactory.getElementSelectorForClass(cssClassname).select(toParse).first();
    return toconvert;
  }
}
