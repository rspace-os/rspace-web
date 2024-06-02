package com.researchspace.webapp.integrations.wopi;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.integrations.wopi.models.xml.XmlAction;
import com.researchspace.webapp.integrations.wopi.models.xml.XmlApp;
import com.researchspace.webapp.integrations.wopi.models.xml.XmlProofKey;
import com.researchspace.webapp.integrations.wopi.models.xml.XmlWopiDiscovery;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import javax.xml.bind.JAXBException;
import org.junit.Test;
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
    assertThat(data.getProofKey().getOldValue(), startsWith("BgIAAAAkAABSU0Ex"));
    assertThat(data.getProofKey().getOldModulus(), startsWith("pZriWYzVpQzWb"));
    assertThat(data.getProofKey().getOldExponent(), is("AQAB"));
    assertThat(data.getProofKey().getValue(), startsWith("BgIAAACkAABSU0E"));
    assertThat(data.getProofKey().getModulus(), startsWith("nHUB27M5goCTt"));
    assertThat(data.getProofKey().getExponent(), is("AQAB"));

    // Validate apps
    assertThat(data.getApps().size(), is(8));
    XmlApp excel = data.getApps().get(0);
    assertThat(excel.getName(), is("Excel"));
    assertThat(data.getApps().get(7).getName(), is("WordPrague"));

    // Validate actions
    assertThat(
        excel.getFavIconUrl(),
        is("https://c1-excel-15.cdn.office.net/x/_layouts/resources/FavIcon_Excel.ico"));
    assertThat(
        excel.getBootstrapperUrl(),
        is("https://c1-excel-15.cdn.office.net/x/s/_layouts/app_scripts/excel-boot.min.js"));
    assertThat(excel.getApplicationBaseUrl(), is("https://excel.officeapps.live.com"));
    assertThat(excel.getStaticResourceOrigin(), is("https://c1-excel-15.cdn.office.net"));
    assertThat(excel.getCheckLicense(), is(true));

    XmlAction viewAction = excel.getActions().get(0);
    assertThat(viewAction.getName(), is("view"));
    assertThat(viewAction.getFileExtension(), is("csv"));
    assertThat(viewAction.isAppDefault(), is(true));
    assertThat(
        viewAction.getUrlSource(),
        is(
            "https://excel.officeapps.live.com/x/_layouts/xlviewerinternal.aspx?"
                + "<ui=UI_LLCC&><rs=DC_LLCC&><dchat=DISABLE_CHAT&><hid=HOST_SESSION_ID&><sc=SESSION_CONTEXT&>"
                + "<wopisrc=WOPI_SOURCE&><IsLicensedUser=BUSINESS_USER&><actnavid=ACTIVITY_NAVIGATION_ID&>"));
    assertThat(viewAction.getRequires(), nullValue());

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    XmlAction editAction =
        excel.getActions().stream().filter(act -> act.getName().equals("edit")).findFirst().get();
    assertThat(editAction.getRequires(), is("update"));

    // Validate proof keys
    XmlProofKey pk = data.getProofKey();
    assertThat(pk.getExponent(), is("AQAB"));
    assertThat(
        pk.getModulus(),
        is(
            "nHUB27M5goCTtfbQv4+nR+eSHLfGrSGM0ReMItC9CT8z39lm3ICSMCWwTg2ZyVELiaS3tlxNCMoEMnr0pqvHvCMCKDgHRKFNFxif8vmqeePqJadqaP9j6YCP6Fejeeuw6nqt12MoIHcBvnOYPCPKWQWqIE+pnS7md6uI3Nh+tBDyVd6Tw+uktVtN17so8jiqZHhgNNH3Sc59EPUJN9qxUd366E4oGHf4YEzHHH4u44S7Mr1QDF3b7KPmqoLmG4v30XnMHsLfQQaKY2QYj1qShUvfgmS9Daf7TST8VIBSYJIyU0SdfJDiTbKc1e/SUIL1U6JJNi2SLfv1Sg8LJAK3YQ=="));
    assertThat(
        pk.getValue(),
        is(
            "BgIAAACkAABSU0ExAAgAAAEAAQBhtwIkCw9K9fstki02SaJT9YJQ0u/VnLJN4pB8nURTMpJgUoBU/CRN+6cNvWSC30uFklqPGGRjigZB38IezHnR94sb5oKq5qPs210MUL0yu4TjLn4cx0xg+HcYKE7o+t1Rsdo3CfUQfc5J99E0YHhkqjjyKLvXTVu1pOvDk95V8hC0ftjciKt35i6dqU8gqgVZyiM8mHO+AXcgKGPXrXrqsOt5o1foj4DpY/9oaqcl6uN5qvnynxgXTaFEBzgoAiO8x6um9HoyBMoITVy2t6SJC1HJmQ1OsCUwkoDcZtnfMz8JvdAijBfRjCGtxrcckudHp4+/0Pa1k4CCObPbAXWc"));
    assertThat(pk.getOldExponent(), is("AQAB"));
    assertThat(
        pk.getOldModulus(),
        is(
            "pZriWYzVpQzWbBaRmX8Jry89QGn0kO8sd/XadCNNGpiw8wCPVY/Sr6RcCshX91Z8OqG6swuwAm7s5Xrda7tUIy+rAIN5r/x9PsnJXjA5re0ktsG1pRjHelll+sDxJI6wsSBOPyvWbOsaoRLJVX06VHMrgX6deBgtg8+EAsxfMVty0PU/QdvYOg4JR6oXr3PrAgnr+VPHzOwRiH225CMPe81BbDBp7Fbbc739bjYHDE4RTgXKMLvYEJfxoMpefC9/mJLLR1fAoA5UjqG9cT5ni6F7VwuKZz2jN3Jamx4ebtj+5moa7eKEu+R5gbjJ15cEGlZK7vVriM7EycRaLPtmXQ=="));
    assertThat(
        pk.getOldValue(),
        is(
            "BgIAAAAkAABSU0ExAAgAAAEAAQBdZvssWsTJxM6Ia/XuSlYaBJfXybiBeeS7hOLtGmrm/thuHh6bWnI3oz1nigtXe6GLZz5xvaGOVA6gwFdHy5KYfy98Xsqg8ZcQ2LswygVOEU4MBzZu/b1z21bsaTBsQc17DyPktn2IEezMx1P56wkC63OvF6pHCQ462NtBP/XQclsxX8wChM+DLRh4nX6BK3NUOn1VyRKhGuts1is/TiCxsI4k8cD6ZVl6xxiltcG2JO2tOTBeyck+ffyveYMAqy8jVLtr3Xrl7G4CsAuzuqE6fFb3V8gKXKSv0o9VjwDzsJgaTSN02vV3LO+Q9GlAPS+vCX+ZkRZs1gyl1YxZ4pql"));
  }
}
