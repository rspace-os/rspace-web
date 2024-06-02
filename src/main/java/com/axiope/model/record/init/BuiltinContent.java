package com.axiope.model.record.init;

import com.researchspace.Constants;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.IRecordFactory;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.UserFolderSetup;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/** All subclasses should provide a default constructor for reflection-based testing. */
public abstract class BuiltinContent implements IBuiltinContent {

  /* the form associated with this content */
  RSForm m_form;

  /* the SampleTemplate associated with this content */
  Sample m_sampleTemplate;

  Logger log = LoggerFactory.getLogger(BuiltinContent.class);

  /* the initializer creating this content */
  IBuiltInPersistor m_initializer;

  IRecordFactory recordFactory;

  private ResourceBundle resourceBundle;

  public BuiltinContent() {
    this.recordFactory = new RecordFactory();
  }

  /*
   * pkg scoped for testing
   */
  void setM_initializer(IBuiltInPersistor m_initializer) {
    this.m_initializer = m_initializer;
  }

  public BuiltinContent(IBuiltInPersistor initializer) {
    this.recordFactory = new RecordFactory();
    m_initializer = initializer;
  }

  @Override
  public abstract RSForm createForm(User createdBy);

  @Override
  public List<StructuredDocument> createTemplates(User createdBy) {
    return null;
  }

  /** Subclasses should override; this returns an empty list. */
  @Override
  public List<StructuredDocument> createExamples(User createdBy, UserFolderSetup folderSetup) {
    return Collections.emptyList();
  }

  /** For use by subclasses to get the resource bundle. Initialises once and then caches. */
  protected ResourceBundle getResourceBundle() {
    if (resourceBundle == null) {
      Locale locale = LocaleContextHolder.getLocale();
      resourceBundle = ResourceBundle.getBundle(Constants.BUNDLE_KEY, locale);
    }

    return resourceBundle;
  }

  protected void markAsTemplate(StructuredDocument template) {
    template.addType(RecordType.TEMPLATE);
    template.setDescription(BaseRecord.TEMPLATE_TYTE_EXT + "_"); // special
    // usage
  }

  /*
   * For use by subclasses to get text and html data to populate fields
   */
  protected String getStartupHTMLData(String path) {
    String theString = getStartupTextData(path);
    // strip newlines
    theString = theString.replaceAll("\\r\\n|\\r|\\n", " ");
    return theString;
  }

  protected String getStartupTextData(String path) {
    Resource resource = new ClassPathResource("StartUpData/" + path);
    StringWriter writer = new StringWriter();
    try {
      IOUtils.copy(resource.getInputStream(), writer, Charset.defaultCharset());
    } catch (IOException e) {
      log.error("Error copying: ", e);
      return " small text , big text couldn't be loaded";
    }
    String theString = writer.toString();
    return theString;
  }

  public boolean isForm(String formName) {
    if (m_form == null) {
      m_form = m_initializer.findFormByName(getFormName());
      if (m_form == null) {
        return false;
      }
    }
    boolean result = formName.equals(m_form.getName());
    return result;
  }

  protected abstract String getFormName();

  @Override
  public List<StructuredDocument> createExamples(
      User createdBy, UserFolderSetup folders, StructuredDocument linkTo) {
    return createExamples(createdBy, folders);
  }

  /*
   * For testing
   */
  void setResourceBundle(ResourceBundle resourceBundle) {
    this.resourceBundle = resourceBundle;
  }

  public void logDebug(String message) {
    log.debug(message);
  }

  public void logWarn(String message) {
    log.warn(message);
  }
}
