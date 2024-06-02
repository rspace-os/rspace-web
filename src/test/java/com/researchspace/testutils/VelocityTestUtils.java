package com.researchspace.testutils;

import java.util.Properties;
import org.apache.velocity.app.VelocityEngine;

public class VelocityTestUtils {

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
    String pathToTFEs = pathToTemplateFolder + "/" + "textFieldElements";
    p.setProperty("file.resource.loader.path", pathToTemplateFolder + "," + pathToTFEs);
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
}
