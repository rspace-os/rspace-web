package com.axiope.webapp.taglib;

import java.io.IOException;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.TagSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Formats a string to make it plural if count > 1. Just adds an 's'. does not do full pluralization
 * of nouns.
 */
public class PluralizeNounTag extends TagSupport {

  private static final Logger LOG = LoggerFactory.getLogger(PluralizeNounTag.class.getName());
  private static final long serialVersionUID = -8491659018947980799L;

  private String input;

  private int count;

  /**
   * @throws JspException if template or action is null
   */
  public int doStartTag() throws JspException {
    try {
      // Get the writer object for output.
      JspWriter out = pageContext.getOut();
      out.println(getString());

    } catch (IOException e) {
      LOG.error("Error writing start tag: ", e);
    }
    return SKIP_BODY;
  }

  String getString() {
    if (count > 1) {
      return (input + "s");
    } else {
      return (input);
    }
  }

  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }

  /**
   * The date to be compared
   *
   * @return
   */
  public String getInput() {
    return input;
  }

  public void setInput(String input) {
    this.input = input;
  }
}
