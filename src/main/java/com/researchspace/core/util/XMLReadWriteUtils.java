package com.researchspace.core.util;

import static java.lang.String.format;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.SchemaOutputResolver;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.ValidationEventHandler;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/** Useful utility methods for writing XML files */
public class XMLReadWriteUtils {
  private static final Logger logger = LoggerFactory.getLogger(XMLReadWriteUtils.class);

  /**
   * Marshalls the specified class to XML
   *
   * @param outFile
   * @param toMarshall
   * @param clazz
   * @throws Exception
   */
  public static <T> void toXML(File outFile, T toMarshall, Class<T> clazz) throws Exception {
    JAXBContext jc = JAXBContext.newInstance(clazz);
    Marshaller m = jc.createMarshaller();

    try (FileOutputStream fos = new FileOutputStream(outFile)) {
      m.marshal(toMarshall, fos);
    }
  }

  // transformder writer doesn't encode surrogate-pair characters into valid UTF-8
  // this seems to be a bug in xalan 2.7.2 which has not been fixed.
  private static void writeFormattedXML(File ouF, StringWriter sw)
      throws FileNotFoundException,
          TransformerFactoryConfigurationError,
          TransformerConfigurationException,
          TransformerException,
          IOException {
    logger.debug("StringWriter content: {}", sw.toString());
    logger.debug("sw to string is: {}", ArrayUtils.toString(sw.toString().getBytes()));
    try (FileOutputStream fos = new FileOutputStream(ouF)) {
      Transformer transformer = configureTransformer();

      StreamResult result = new StreamResult(fos);

      transformer.transform(new StreamSource(new StringReader(sw.toString())), result);
      fos.flush();
    }
  }

  private static Transformer configureTransformer() throws TransformerConfigurationException {
    TransformerFactory factory = TransformerFactory.newInstance();
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
    Transformer transformer = factory.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
    return transformer;
  }

  /**
   * Unmarshalls an XML file to an object
   *
   * @param xmlFile The XML file to unmarshal
   * @param clazz The class of the root object in the XML file
   * @param schemaFile An optional XSD schema file. If not null, validation of the XML file against
   *     the schema will be performed.
   * @param eventHandler An optional event handler to handle errors in validation of the document
   * @return The root object of the XML tree
   * @throws SAXException
   * @throws FileNotFoundException
   * @throws Exception This wraps many low-level JAXB exceptions
   */
  public static <T> T fromXML(
      File xmlFile, Class<T> clazz, File schemaFile, ValidationEventHandler eventHandler)
      throws JAXBException, SAXException, FileNotFoundException {

    JAXBContext jc = JAXBContext.newInstance(clazz);
    Unmarshaller unmarshaller = jc.createUnmarshaller();

    if (schemaFile != null) {
      SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      sf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      sf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      Schema schema = sf.newSchema(schemaFile);
      unmarshaller.setSchema(schema);
      if (eventHandler != null) {
        unmarshaller.setEventHandler(eventHandler);
      }
    }

    SAXParserFactory spf = SAXParserFactory.newInstance();
    spf.setNamespaceAware(true);
    try {
      spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      spf.setXIncludeAware(false);
      XMLReader xmlReader = spf.newSAXParser().getXMLReader();
      Source source =
          new SAXSource(
              xmlReader, new InputSource(new BufferedInputStream(new FileInputStream(xmlFile))));
      return (T) unmarshaller.unmarshal(source);
    } catch (ParserConfigurationException e) {
      throw new SAXException("Error configuring SAX parser", e);
    }
  }

  public static <T> void generateSchemaFromXML(File outFile, Class<T> clazz)
      throws JAXBException, IOException {
    JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
    SchemaOutputResolver sor = new ArchivalSchemaOut(outFile);
    jaxbContext.generateSchema(sor);
  }

  /**
   * Converts a JAX-B annotated object to a W3CDocument object
   *
   * @param object
   * @param clazz
   * @return
   * @throws JAXBException
   * @throws ParserConfigurationException
   */
  public static <T> Document convertObjectToW3CDocument(T object, Class<T> clazz)
      throws JAXBException, ParserConfigurationException {
    JAXBContext jc = JAXBContext.newInstance(clazz);

    // Create the Document
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    dbf.setXIncludeAware(false);

    DocumentBuilder db = dbf.newDocumentBuilder();
    Document document = db.newDocument();

    // Marshal the Object to a Document
    Marshaller marshaller = jc.createMarshaller();
    marshaller.marshal(object, document);
    return document;
  }

  /**
   * Writes a W3CDocument to formatted with a default 4 space indent, to an OutputStream. <br>
   * It is the callers responsibility to manage the {@link OutputStream}
   *
   * @param document A {@link Document}
   * @param outStream
   * @throws TransformerFactoryConfigurationError
   * @throws TransformerException
   * @throws FileNotFoundException
   * @throws {@link IllegalArgumentException} if either argument is null;
   */
  public static void writeFormattedXML(Document document, OutputStream outStream)
      throws TransformerFactoryConfigurationError, TransformerException, FileNotFoundException {
    Validate.noNullElements(
        new Object[] {document, outStream},
        format("Arguments cannot be null but were: " + document + ", " + outStream));
    Transformer transformer = configureTransformer();
    StreamResult streamResult = new StreamResult(outStream);
    DOMSource domSource = new DOMSource(document);
    transformer.transform(domSource, streamResult);
  }

  static class ArchivalSchemaOut extends SchemaOutputResolver {
    File xsdFile;

    public ArchivalSchemaOut(File xsdFile) {
      super();
      this.xsdFile = xsdFile;
    }

    public Result createOutput(String namespaceURI, String suggestedFileName) throws IOException {
      StreamResult result = new StreamResult(xsdFile);
      result.setSystemId(xsdFile.toURI().toURL().toString());
      return result;
    }
  }
}
