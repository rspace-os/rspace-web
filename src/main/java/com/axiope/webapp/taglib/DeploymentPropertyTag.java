package com.axiope.webapp.taglib;

import com.axiope.webapp.listener.StartupListener;
import com.researchspace.properties.IPropertyHolder;
import java.lang.reflect.InvocationTargetException;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Compares property name and value in tag with that of the injected property in PropertyHolder
 * class.
 */
public class DeploymentPropertyTag extends TagSupport {

  private static final long serialVersionUID = -2545513925674998973L;
  private String name;
  private String value;
  private boolean match = true;
  private boolean testNonBlankOnly = false;

  /**
   * Set this to true if you just want to see if a property has a value. In this case the value and
   * match attributes are ignored.
   *
   * @return
   */
  public boolean isTestNonBlankOnly() {
    return testNonBlankOnly;
  }

  public void setTestNonBlankOnly(boolean testNonBlankOnly) {
    this.testNonBlankOnly = testNonBlankOnly;
  }

  /**
   * @return the match
   */
  public boolean isMatch() {
    return match;
  }

  /**
   * @param match the match to set
   */
  public void setMatch(boolean match) {
    this.match = match;
  }

  /**
   * @throws JspException if template or action is null
   */
  public int doStartTag() throws JspException {
    boolean isMatch = checkProperty();
    if (isMatch && match || (!isMatch && !match)) {
      return TagSupport.EVAL_BODY_INCLUDE;
    } else {
      return TagSupport.SKIP_BODY;
    }
  }

  private boolean checkProperty() {
    Object val = getPropertyHolderFromServetContext();
    if (val != null && val instanceof IPropertyHolder) {
      try {
        String propertyValue = BeanUtils.getSimpleProperty(val, name);
        if (testNonBlankOnly) {
          return !StringUtils.isBlank(propertyValue);
        }
        if (propertyValue != null && propertyValue.equals(value)) {
          return true;
        }
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        return false;
      }
    }
    return false;
  }

  Object getPropertyHolderFromServetContext() {
    return pageContext
        .getServletContext()
        .getAttribute(StartupListener.RS_DEPLOY_PROPS_CTX_ATTR_NAME);
  }

  /**
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * @param name the name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * @return the value
   */
  public String getValue() {
    return value;
  }

  /**
   * @param value the value to set
   */
  public void setValue(String value) {
    this.value = value;
  }

  /**
   * @return the serialversionuid
   */
  public static long getSerialversionuid() {
    return serialVersionUID;
  }
}
