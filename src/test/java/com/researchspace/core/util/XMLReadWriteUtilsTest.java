package com.researchspace.core.util;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;

import jakarta.xml.bind.UnmarshalException;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

public class XMLReadWriteUtilsTest {
	private static final String TEST_XML_ROOT_OBJECT_ELEMENT_NAME = "testXMLRootObject";
	File tmpFile;

	@BeforeEach
	public void setUp() throws Exception {
		tmpFile = File.createTempFile("archive", "xml");
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void roundTripWithNoValidationHappyCase() throws Exception {
		TestXMLRootObject af = new TestXMLRootObject(1L, "x");
		XMLReadWriteUtils.toXML(tmpFile, af, TestXMLRootObject.class);
		TestXMLRootObject imported = XMLReadWriteUtils.fromXML(tmpFile, TestXMLRootObject.class, null, null);
		assertNotNull(imported);
		assertEquals(af, imported);
	}
	
	@Test
	public void roundTripWithUTF8HappyCase() throws Exception {
	
		TestXMLRootObject af = new TestXMLRootObject(1L, "<p class='xxx'>😊</p>");
		XMLReadWriteUtils.toXML(tmpFile, af, TestXMLRootObject.class);
		TestXMLRootObject imported = XMLReadWriteUtils.fromXML(tmpFile, TestXMLRootObject.class, null, null);
		System.err.println(FileUtils.readFileToString(tmpFile, "UTF-8"));
		assertNotNull(imported);
		assertEquals(af, imported);
		System.err.println(imported.getData());
	}
	
	@Test
	public void binToHex(){
		byte[] bytes = {-16,-97,-104,-118 }; // smilie 
	    StringBuilder sb = new StringBuilder();
	    for (byte b : bytes) {
	    	int res = b & 0xFF;
	        System.out.println("mask = "+res);
	        sb.append(String.format("%X ", b));
	    }
	    System.out.println(sb.toString());
	}

	@Test()
	public void roundTripWithInvalidSchemaThrowsUnmarshalException() throws Exception {
		TestXMLRootObject af = new TestXMLRootObject(1L, "y");
		XMLReadWriteUtils.toXML(tmpFile, af, TestXMLRootObject.class);
		File invalidSchema = new File("src/test/resources/zipSchema.xsd");
		assertThrows(UnmarshalException.class, ()->XMLReadWriteUtils.fromXML(tmpFile, TestXMLRootObject.class, invalidSchema, null));
	}
	
	@Test
	public void convertObjectToW3CDocument() throws Exception {
		TestXMLRootObject toConvert = new TestXMLRootObject(1L, "x");
		Document doc = XMLReadWriteUtils.convertObjectToW3CDocument(toConvert, TestXMLRootObject.class);
		assertNotNull(doc);
		assertEquals(TEST_XML_ROOT_OBJECT_ELEMENT_NAME,doc.getDocumentElement().getTagName());
	}
	
	@Test
	public void writeFormattedOutput() throws Exception {
		TestXMLRootObject toConvert = new TestXMLRootObject(1L, "x");
		Document doc = XMLReadWriteUtils.convertObjectToW3CDocument(toConvert, TestXMLRootObject.class);
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			XMLReadWriteUtils.writeFormattedXML(doc, baos);
			String output = baos.toString(Charset.defaultCharset());
			assertTrue (output.contains(TEST_XML_ROOT_OBJECT_ELEMENT_NAME));
		}
		
	}
	@Test
	public void fromXMLWithDocTypeThrowsException() throws Exception {
		String xxeXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
						"<!DOCTYPE testXMLRootObject>\n" +
						"<testXMLRootObject id=\"1\">\n" +
						"  <data>&xxe;</data>\n" +
						"</testXMLRootObject>";
		FileUtils.writeStringToFile(tmpFile, xxeXml, Charset.defaultCharset());
		
		UnmarshalException exception = assertThrows(UnmarshalException.class, () -> {
			XMLReadWriteUtils.fromXML(tmpFile, TestXMLRootObject.class, null, null);
		});

		assertTrue(exception.toString().contains("DOCTYPE is disallowed when the feature " +
						"\"http://apache.org/xml/features/disallow-doctype-decl\" set to true."));
	}
}
