package com.researchspace.linkedelements;

import com.researchspace.dao.AuditDao;
import com.researchspace.model.User;
import com.researchspace.testutils.TestFactory;
import com.researchspace.testutils.VelocityTestUtils;
import org.apache.velocity.app.VelocityEngine;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractParserTest {

  protected @Mock AuditDao auditDao;

  protected RichTextUpdater rtu;
  protected ElementSelectorFactory selectorFactory;

  protected User anyUser;
  protected FieldContents contents;
  protected VelocityEngine velocity;

  @BeforeEach
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
