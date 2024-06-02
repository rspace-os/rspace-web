package com.axiope.webapp.taglib;

import static java.lang.Math.min;
import static org.apache.commons.lang.StringUtils.join;

import com.researchspace.core.util.ObjectToStringPropertyTransformer;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.TagSupport;
import org.apache.commons.lang.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Produces a concatenated comma separated list of object properties. Setting maxSize allows a limit
 * on the number of values in the collection output to string. <br>
 * Truncated lists are terminated by an ellipsis ...
 */
public class JoinPropertyStringFromCollection extends TagSupport {

  private static final Logger LOG = LoggerFactory.getLogger(JoinPropertyStringFromCollection.class);
  private static final long serialVersionUID = -8491659018947980799L;

  private Collection<?> collection;

  private String property;

  private int maxSize = Integer.MAX_VALUE; // default unlimited

  /**
   * Get the maximum number of elements from the collection to display.
   *
   * @return the maxSize
   */
  public int getMaxSize() {
    return maxSize;
  }

  /**
   * @param maxSize the maxSize to set, must be >=1
   */
  public void setMaxSize(int maxSize) {
    if (maxSize < 1) {
      throw new IllegalArgumentException(
          "There must be at least 1 element written, but was " + maxSize);
    }
    this.maxSize = maxSize;
  }

  public Collection<?> getCollection() {
    return collection;
  }

  public void setCollection(Collection<?> collection) {
    this.collection = collection;
  }

  public String getProperty() {
    return property;
  }

  public void setProperty(String property) {
    this.property = property;
  }

  public static long getSerialversionuid() {
    return serialVersionUID;
  }

  /**
   * @throws JspException if template or action is null
   */
  public int doStartTag() throws JspException {
    try {
      // Get the writer object for output.
      JspWriter out = pageContext.getOut();
      String output = getOutputString();
      out.println(output);
    } catch (IOException e) {
      LOG.error("Error writing output: ", e);
    }
    return SKIP_BODY;
  }

  String getOutputString() {
    List<String> propertyList =
        collection.stream()
            .map(new ObjectToStringPropertyTransformer<Object>(property))
            .collect(Collectors.toList());
    int endIndex = min(propertyList.size(), maxSize);
    String rc = join(propertyList.toArray(), ", ", 0, endIndex);
    if (propertyList.size() > maxSize) {
      rc = rc + "...";
    }
    return StringEscapeUtils.escapeHtml(rc);
  }
}
