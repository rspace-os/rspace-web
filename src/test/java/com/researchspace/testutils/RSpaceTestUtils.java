package com.researchspace.testutils;

import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.researchspace.auth.SSOAuthenticationToken;
import com.researchspace.core.testutil.Invokable;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import javax.imageio.ImageIO;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.subject.Subject;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;

public class RSpaceTestUtils {

  /**
   * Standard login for
   *
   * @param uname
   * @param pw
   */
  public static void login(String uname, String pw) {
    Subject subject = SecurityUtils.getSubject();
    subject.login(new UsernamePasswordToken(uname, pw));
  }

  /**
   * Alternative login for testing aspects of SSO login
   *
   * @param ssoUsername
   */
  public static void loginSSO(String ssoUsername) {
    SecurityUtils.getSubject().login(new SSOAuthenticationToken(ssoUsername));
  }

  public static void logout() {
    SecurityUtils.getSubject().logout();
  }

  /**
   * Replaces currently logged in user with new user
   *
   * @param uname
   * @param pw
   */
  public static void logoutCurrUserAndLoginAs(String uname, String pw) {
    logout();
    login(uname, pw);
  }

  /**
   * Gets a named resource in src/test/resources/TestResources/, as a byte array.
   *
   * @param fileName
   * @return
   * @throws IOException
   */
  public static byte[] getResourceAsByteArray(String fileName) throws IOException {
    return IOUtils.toByteArray(getInputStreamOnFromTestResourcesFolder(fileName));
  }

  /**
   * Gets example field content with links
   *
   * @return
   */
  public static String getExampleFieldContent() {
    return getAsString("exampleLinks.txt");
  }

  static String getAsString(String resource) {
    try {
      InputStream is = getInputStreamOnFromTestResourcesFolder(resource);
      return StringUtils.join(IOUtils.readLines(is, "UTF-8"), "\\n");
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Gets a json string of an annotation or sketch
   *
   * @return
   */
  public static String getAnyAnnotationOrSketchJson() {
    return getAsString("zwibbler.json");
  }

  /**
   * Asserts that an AuthorizationException is thrown.
   *
   * @param invokable
   * @throws Exception
   */
  public static void assertAuthExceptionThrown(Invokable invokable) throws Exception {
    assertExceptionThrown(invokable, AuthorizationException.class);
  }

  /**
   * Gets example mol format string or use in tests
   *
   * @return
   * @throws IOException
   */
  public static String getExampleChemString() throws IOException {

    InputStream molInput = getInputStreamOnFromTestResourcesFolder("Amfetamine.mol");
    String newChemElementMolString = IOUtils.toString(molInput, StandardCharsets.UTF_8);

    molInput.close();
    return newChemElementMolString;
  }

  /**
   * Gets mol format string from specified file for use in tests
   *
   * @return
   * @throws IOException
   */
  public static String getMolString(String fileName) throws IOException {

    try {
      InputStream molInput = getInputStreamOnFromTestResourcesFolder(fileName);
      String newChemElementMolString = IOUtils.toString(molInput, "UTF-8");
      molInput.close();
      return newChemElementMolString;
    } catch (Exception e) {
      e.printStackTrace();
      fail("Error coverting file into mol string: " + e.getMessage());
      return "";
    }
  }

  /**
   * Gets example chem element diagram as a base64 string for use in tests
   *
   * @return
   * @throws IOException
   */
  public static String getChemImage() throws IOException {
    return "Image," + FileUtils.readFileToString(getResource("base64Image.txt"), "UTF-8");
  }

  /**
   * Given the name of a file in src/test/resources/TestResources/, returns an Input stream to it.
   * CLient should close the input stream.
   *
   * @param fileName
   * @return
   * @throws IOException
   */
  public static InputStream getInputStreamOnFromTestResourcesFolder(String fileName)
      throws IOException {
    InputStream is =
        RSpaceTestUtils.class.getClassLoader().getResourceAsStream("TestResources/" + fileName);
    return is;
  }

  public static String loadTextResourceFromPdfDir(String name) throws IOException {
    return FileUtils.readFileToString(RSpaceTestUtils.getResource("pdf/" + name), "UTF-8");
  }

  /**
   * Gets a test file, specified by its name relative to TestResources folder.
   *
   * @param fileName
   * @return
   */
  public static File getResource(String fileName) {
    File resource = new File("src/test/resources/TestResources/" + fileName);
    return resource;
  }

  public static File getAnyPdf() {
    return getResource("smartscotland3.pdf");
  }

  /**
   * Given the name of an image file in src/test/resources/TestResources/, returns a BuffereDImage
   * of it.
   *
   * @param fileName
   * @return
   * @throws IOException
   */
  public static BufferedImage getImageFromTestResourcesFolder(String fileName) throws IOException {
    File resource = getResource(fileName);
    BufferedImage bimg = ImageIO.read(resource);
    return bimg;
  }

  /**
   * Gets a small plain text file for testing attachment behaviour
   *
   * @return
   */
  public static File getAnyAttachment() {
    return getResource("genFilesi.txt");
  }

  /**
   * Gets a Genabnkfile
   *
   * @return
   */
  public static File getAnyGenbankFile() {
    return getResource("alpha-2-macroglobulin.gb");
  }

  /**
   * Sets up a Velocity engine for template rendering, for non-Spring unit tests that require
   * Velocity.
   *
   * @param pathToTemplateFolder
   * @return
   */
  public static VelocityEngine setupVelocity(String pathToTemplateFolder) {
    Properties p = new Properties();
    VelocityEngine vel = new VelocityEngine();
    p.setProperty("file.resource.loader.path", pathToTemplateFolder);
    vel.init(p);
    return vel;
  }

  /**
   * Sets up a Velocity engine for template rendering, for non-Spring unit tests that require
   * Velocity. Configures access to text-fiel element templates
   *
   * @return
   */
  public static VelocityEngine setupVelocityWithTextFieldTemplates() {
    Properties p = new Properties();
    VelocityEngine vel = new VelocityEngine();
    p.setProperty(
        "file.resource.loader.path", "src/main/resources/velocityTemplates/textFieldElements");
    vel.init(p);
    return vel;
  }

  /**
   * Creates a test message source with supplied key-value messagecodes:messages
   *
   * @param messages
   * @return
   */
  public static MessageSource messageSource(Map<String, String> messages) {
    StaticMessageSource sms = new StaticMessageSource();
    sms.addMessages(messages, Locale.getDefault());
    return sms;
  }

  /**
   * Tries to serialize provided object
   *
   * @param object to serialize
   */
  public static void assertObjectSerializable(Object objectToSerialize) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeObject(objectToSerialize);
      byte[] serializedSC = baos.toByteArray();
      assertNotNull(serializedSC);
    } catch (IOException e) {
      e.printStackTrace();
      fail("problem with serializing an object: " + e.getMessage());
    }
  }
}
