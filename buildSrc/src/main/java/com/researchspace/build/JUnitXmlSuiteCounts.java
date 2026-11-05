package com.researchspace.build;

import java.io.File;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Recounts each JUnit XML test suite from its test cases, so a test that failed and then passed on
 * a retry counts as passed.
 *
 * <p>With {@code mergeReruns}, Gradle writes such a test as one test case holding a {@code
 * flakyFailure}, as Surefire does, but still counts the failed attempt in the suite's {@code
 * failures} attribute. Report publishers read that attribute (GitHub's test-results check fails the
 * run, Jenkins marks it unstable), so rewrite it the way Surefire did: only test cases with a
 * {@code failure}, {@code error} or {@code skipped} child count.
 */
public final class JUnitXmlSuiteCounts {

  private JUnitXmlSuiteCounts() {}

  /** Rewrites the counts of every {@code TEST-*.xml} report in {@code dir}. */
  public static void recount(File dir) throws IOException {
    File[] reports = dir.listFiles((d, name) -> name.startsWith("TEST-") && name.endsWith(".xml"));
    if (reports == null) {
      return;
    }
    for (File report : reports) {
      recountFile(report);
    }
  }

  private static void recountFile(File report) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document doc = factory.newDocumentBuilder().parse(report);
      Element suite = doc.getDocumentElement();
      if (!"testsuite".equals(suite.getTagName()) || !containsFlakyRerun(suite)) {
        return;
      }
      int tests = 0;
      int failures = 0;
      int errors = 0;
      int skipped = 0;
      NodeList children = suite.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        if (children.item(i) instanceof Element testcase
            && "testcase".equals(testcase.getTagName())) {
          tests++;
          failures += hasChild(testcase, "failure") ? 1 : 0;
          errors += hasChild(testcase, "error") ? 1 : 0;
          skipped += hasChild(testcase, "skipped") ? 1 : 0;
        }
      }
      suite.setAttribute("tests", Integer.toString(tests));
      suite.setAttribute("failures", Integer.toString(failures));
      suite.setAttribute("errors", Integer.toString(errors));
      suite.setAttribute("skipped", Integer.toString(skipped));
      var transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.transform(new DOMSource(doc), new StreamResult(report));
    } catch (ParserConfigurationException | SAXException | TransformerException e) {
      throw new IOException("Could not recount " + report, e);
    }
  }

  private static boolean containsFlakyRerun(Element suite) {
    return suite.getElementsByTagName("flakyFailure").getLength() > 0
        || suite.getElementsByTagName("flakyError").getLength() > 0;
  }

  private static boolean hasChild(Element element, String tag) {
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element e && tag.equals(e.getTagName())) {
        return true;
      }
    }
    return false;
  }
}
