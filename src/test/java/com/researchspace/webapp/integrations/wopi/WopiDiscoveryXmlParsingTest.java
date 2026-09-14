package com.researchspace.webapp.integrations.wopi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.integrations.wopi.models.xml.XmlAction;
import com.researchspace.webapp.integrations.wopi.models.xml.XmlApp;
import com.researchspace.webapp.integrations.wopi.models.xml.XmlProofKey;
import com.researchspace.webapp.integrations.wopi.models.xml.XmlWopiDiscovery;
import jakarta.xml.bind.JAXBException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class WopiDiscoveryXmlParsingTest extends SpringTransactionalTest {

  private static File EXAMPLE_DISCOVERY_XML_FILE =
      RSpaceTestUtils.getResource("officeOnlineDiscovery.xml");

  @Autowired private WopiDiscoveryProcessor processor;

  @Test
  public void parseSavedXml() throws JAXBException, FileNotFoundException {
    InputStreamReader reader = new FileReader(EXAMPLE_DISCOVERY_XML_FILE);
    XmlWopiDiscovery data = processor.parseDiscoveryXml(reader);

    // Validate proof key info got read correctly
    assertTrue(
        data.getProofKey().getOldValue().startsWith("BgIAAAAkAABSU0Ex"),
        data.getProofKey().getOldValue());
    assertTrue(
        data.getProofKey().getOldModulus().startsWith("pZriWYzVpQzWb"),
        data.getProofKey().getOldModulus());
    assertEquals("AQAB", data.getProofKey().getOldExponent());
    assertTrue(
        data.getProofKey().getValue().startsWith("BgIAAACkAABSU0E"), data.getProofKey().getValue());
    assertTrue(
        data.getProofKey().getModulus().startsWith("nHUB27M5goCTt"),
        data.getProofKey().getModulus());
    assertEquals("AQAB", data.getProofKey().getExponent());

    // Validate apps
    assertEquals(8, data.getApps().size());
    XmlApp excel = data.getApps().get(0);
    assertEquals("Excel", excel.getName());
    assertEquals("WordPrague", data.getApps().get(7).getName());

    // Validate actions
    assertEquals(
        "https://c1-excel-15.cdn.office.net/x/_layouts/resources/FavIcon_Excel.ico",
        excel.getFavIconUrl());
    assertEquals(
        "https://c1-excel-15.cdn.office.net/x/s/_layouts/app_scripts/excel-boot.min.js",
        excel.getBootstrapperUrl());
    assertEquals("https://excel.officeapps.live.com", excel.getApplicationBaseUrl());
    assertEquals("https://c1-excel-15.cdn.office.net", excel.getStaticResourceOrigin());
    assertEquals(true, excel.getCheckLicense());

    XmlAction viewAction = excel.getActions().get(0);
    assertEquals("view", viewAction.getName());
    assertEquals("csv", viewAction.getFileExtension());
    assertEquals(true, viewAction.isAppDefault());
    assertEquals(
        "https://excel.officeapps.live.com/x/_layouts/xlviewerinternal.aspx?"
            + "<ui=UI_LLCC&><rs=DC_LLCC&><dchat=DISABLE_CHAT&><hid=HOST_SESSION_ID&><sc=SESSION_CONTEXT&>"
            + "<wopisrc=WOPI_SOURCE&><IsLicensedUser=BUSINESS_USER&><actnavid=ACTIVITY_NAVIGATION_ID&>",
        viewAction.getUrlSource());
    assertNull(viewAction.getRequires());

    XmlAction editAction =
        excel.getActions().stream()
            .filter(act -> act.getName().equals("edit"))
            .findFirst()
            .orElseThrow();
    assertEquals("update", editAction.getRequires());

    // Validate proof keys
    XmlProofKey pk = data.getProofKey();
    assertEquals("AQAB", pk.getExponent());
    assertEquals(
        "nHUB27M5goCTtfbQv4+nR+eSHLfGrSGM0ReMItC9CT8z39lm3ICSMCWwTg2ZyVELiaS3tlxNCMoEMnr0pqvHvCMCKDgHRKFNFxif8vmqeePqJadqaP9j6YCP6Fejeeuw6nqt12MoIHcBvnOYPCPKWQWqIE+pnS7md6uI3Nh+tBDyVd6Tw+uktVtN17so8jiqZHhgNNH3Sc59EPUJN9qxUd366E4oGHf4YEzHHH4u44S7Mr1QDF3b7KPmqoLmG4v30XnMHsLfQQaKY2QYj1qShUvfgmS9Daf7TST8VIBSYJIyU0SdfJDiTbKc1e/SUIL1U6JJNi2SLfv1Sg8LJAK3YQ==",
        pk.getModulus());
    assertEquals(
        "BgIAAACkAABSU0ExAAgAAAEAAQBhtwIkCw9K9fstki02SaJT9YJQ0u/VnLJN4pB8nURTMpJgUoBU/CRN+6cNvWSC30uFklqPGGRjigZB38IezHnR94sb5oKq5qPs210MUL0yu4TjLn4cx0xg+HcYKE7o+t1Rsdo3CfUQfc5J99E0YHhkqjjyKLvXTVu1pOvDk95V8hC0ftjciKt35i6dqU8gqgVZyiM8mHO+AXcgKGPXrXrqsOt5o1foj4DpY/9oaqcl6uN5qvnynxgXTaFEBzgoAiO8x6um9HoyBMoITVy2t6SJC1HJmQ1OsCUwkoDcZtnfMz8JvdAijBfRjCGtxrcckudHp4+/0Pa1k4CCObPbAXWc",
        pk.getValue());
    assertEquals("AQAB", pk.getOldExponent());
    assertEquals(
        "pZriWYzVpQzWbBaRmX8Jry89QGn0kO8sd/XadCNNGpiw8wCPVY/Sr6RcCshX91Z8OqG6swuwAm7s5Xrda7tUIy+rAIN5r/x9PsnJXjA5re0ktsG1pRjHelll+sDxJI6wsSBOPyvWbOsaoRLJVX06VHMrgX6deBgtg8+EAsxfMVty0PU/QdvYOg4JR6oXr3PrAgnr+VPHzOwRiH225CMPe81BbDBp7Fbbc739bjYHDE4RTgXKMLvYEJfxoMpefC9/mJLLR1fAoA5UjqG9cT5ni6F7VwuKZz2jN3Jamx4ebtj+5moa7eKEu+R5gbjJ15cEGlZK7vVriM7EycRaLPtmXQ==",
        pk.getOldModulus());
    assertEquals(
        "BgIAAAAkAABSU0ExAAgAAAEAAQBdZvssWsTJxM6Ia/XuSlYaBJfXybiBeeS7hOLtGmrm/thuHh6bWnI3oz1nigtXe6GLZz5xvaGOVA6gwFdHy5KYfy98Xsqg8ZcQ2LswygVOEU4MBzZu/b1z21bsaTBsQc17DyPktn2IEezMx1P56wkC63OvF6pHCQ462NtBP/XQclsxX8wChM+DLRh4nX6BK3NUOn1VyRKhGuts1is/TiCxsI4k8cD6ZVl6xxiltcG2JO2tOTBeyck+ffyveYMAqy8jVLtr3Xrl7G4CsAuzuqE6fFb3V8gKXKSv0o9VjwDzsJgaTSN02vV3LO+Q9GlAPS+vCX+ZkRZs1gyl1YxZ4pql",
        pk.getOldValue());
  }
}
