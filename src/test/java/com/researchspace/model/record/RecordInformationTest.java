package com.researchspace.model.record;

import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.researchspace.model.EcatImage;
import com.researchspace.model.User;

public class RecordInformationTest {
	
	BaseRecord doc,doc1, folder, folder1, image, image1;
	User anyUser;

	@BeforeEach
	void setUp() throws Exception {
		anyUser = TestFactory.createAnyUser("any");
		doc = createDoc(1L);
		doc1 =createDoc(4L);
		folder =createFolder(2);
		image = createImage(3L);
		Thread.sleep(1);
		
		folder1 = createFolder(5);
		image1 = createImage(6L);
	}

	private EcatImage createImage(long id) {
		EcatImage image1 = TestFactory.createEcatImage(id);
		image1.setOwner(anyUser);
		return image1;
	}

	private BaseRecord createFolder(long id) {
		Folder folder = TestFactory.createAFolder("f1", anyUser);
		folder.setId(id);
		return folder;
	}

	private StructuredDocument createDoc(long id) {
		StructuredDocument doc = TestFactory.createAnySD();
		doc.setOwner(anyUser);
		doc.setId(id);
		return doc;
	}

	@AfterEach
	void tearDown() throws Exception {
	}

	@Test
	@DisplayName("Different ids make 2 RecordInfos not equal")
	void hashCodeAndEqualsForDifferentProperties() {
		// distinct objects with different ids and creation times are not the same
		assertFalse(doc.equals(doc1));
		assertFalse(folder.equals(folder1));
		assertFalse(image.equals(image1));
		assertNotEquals(doc.hashCode() , doc1.hashCode());
		assertNotEquals(folder.hashCode() , folder1.hashCode());
		assertNotEquals(image.hashCode() , image1.hashCode());
		RecordInformation ri = new RecordInformation(doc);
		RecordInformation ri1 = new RecordInformation(doc1);
		assertFalse(ri.equals(ri1));
		assertFalse(ri.hashCode() == ri1.hashCode());
	}
	
	@Test
	@DisplayName("If 2 Folders are equal, their Record Informations should be equal")
	void equalFoldersAreEqualRecordInformations() {
		BaseRecord equlToFolder = createFolder(2L);
		equlToFolder.setCreationDate(folder.getCreationDate());
		equlToFolder.setModificationDate(folder.getModificationDateAsDate());
		assertEquals(equlToFolder, folder);
		assertEquals(equlToFolder.hashCode(), folder.hashCode());
		RecordInformation ri = new RecordInformation(folder);
		RecordInformation ri1 = new RecordInformation(equlToFolder);
		assertEquals(ri, ri1);
		assertEquals(ri.hashCode(), ri1.hashCode());	
	}
	
	@Test
	@DisplayName("If 2 Images are equal, their Record Informations should be equal")
	void equalImagesAreEqualRecordInformations() {
		BaseRecord equlToImage = createImage(3L);
		equlToImage.setCreationDate(image.getCreationDate());
		equlToImage.setModificationDate(image.getModificationDateAsDate());
		assertEquals(equlToImage, image);
		assertEquals(equlToImage.hashCode(), image.hashCode());
		RecordInformation ri = new RecordInformation(image);
		RecordInformation ri1 = new RecordInformation(equlToImage);
		assertEquals(ri, ri1);
		assertEquals(ri.hashCode(), ri1.hashCode());	
	}
	
	@Test
	@DisplayName("If 2 StructDocs are equal, their Record Informations should be equal")
	void equalDocsAreEqualRecordInformations() {
		BaseRecord equlToDoc = createDoc(1L);
		equlToDoc.setCreationDate(doc.getCreationDate());
		equlToDoc.setModificationDate(doc.getModificationDateAsDate());
		assertEquals(equlToDoc, doc);
		assertEquals(equlToDoc.hashCode(), doc.hashCode());
		RecordInformation ri = new RecordInformation(doc);
		RecordInformation ri1 = new RecordInformation(equlToDoc);
		assertEquals(ri, ri1);
		assertEquals(ri.hashCode(), ri1.hashCode());	
	}



}
