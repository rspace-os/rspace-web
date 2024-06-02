package com.axiope.model.record.init;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.PropertyResourceBundle;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.reflections.Reflections;

public class BuiltInsCreatedProperlyTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock IBuiltInPersistor persistor;

  RichTextUpdater richTextUpdater = new RichTextUpdater();

  User user = TestFactory.createAnyUser("anyuser");
  PropertyResourceBundle bundle = null;

  @Before
  public void setUp() throws Exception {
    // need to read this is in as only has access to test resources by defautlt
    bundle =
        new PropertyResourceBundle(
            new FileInputStream("src/main/resources/bundles/ApplicationResources.properties"));
  }

  List<BuiltinContent> getBuiltinsInPackage()
      throws InstantiationException, IllegalAccessException {
    List<BuiltinContent> rc = new ArrayList<>();

    Reflections reflections = new Reflections("com.axiope.model.record.init");

    Set<Class<? extends BuiltinContent>> subTypes = reflections.getSubTypesOf(BuiltinContent.class);
    for (Class<? extends BuiltinContent> clazz : subTypes) {
      // ignore notebook as is created differently

      BuiltinContent content = clazz.newInstance();
      rc.add(content);
    }
    return rc;
  }

  /**
   * Uses reflection to scan all subclasses of builtins and makes basic assertions. All creation
   * code is exercised, which validates resource keys etc.
   *
   * @throws InstantiationException
   * @throws IllegalAccessException
   */
  @Test
  public void testBuiltInCreation() throws InstantiationException, IllegalAccessException {
    List<BuiltinContent> builtins = getBuiltinsInPackage();
    for (BuiltinContent builtin : builtins) {
      // ignore notebook as is created differently
      if (builtin.getClass().isAssignableFrom(BuiltinNotebook.class)
          || builtin.getClass().isAssignableFrom(Welcome.class)
          || SampleTemplateBuiltIn.class.isAssignableFrom(builtin.getClass())) {
        continue;
      }
      builtin.setResourceBundle(bundle);
      builtin.setM_initializer(persistor);

      assertNotNull(builtin.getFormIconName());
      assertNotNull(builtin.getFormName());
      assertNotNull(builtin.createForm(user));
      List<StructuredDocument> templates = builtin.createTemplates(user);
      for (StructuredDocument sd : templates) {
        assertTrue(sd.isTemplate());
      }
      UserFolderSetupImpl folderSetup = new UserFolderSetupImpl();
      List<StructuredDocument> examples = builtin.createExamples(user, folderSetup);
      assertNotNull(examples);
      for (StructuredDocument doc : examples) {
        assertTrue(doc.hasType(RecordType.NORMAL));
      }
    }
    verify(persistor, Mockito.atLeast(1)).saveRecord(Mockito.any(StructuredDocument.class));
    Mockito.verify(persistor, Mockito.atLeast(1)).saveForm(Mockito.any(RSForm.class));
  }
}
