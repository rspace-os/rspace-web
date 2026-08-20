package com.researchspace.model;

import static com.researchspace.model.RSpaceModelTestUtils.getAnyImage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.researchspace.model.record.TestFactory;

class EcatImageTest {
	EcatImage anyImage = null;
	private User anyUser;

	@BeforeEach
	void setUp() throws Exception {
		anyImage = TestFactory.createEcatImage(2L);
		anyUser = TestFactory.createAnyUser("any");
	}

	@AfterEach
	void tearDown() throws Exception {
	}

	@Test
	@DisplayName("Setting rotation requires values 0-3 inc")
	void setRotation() {
		//default value
		assertEquals (0, anyImage.getRotation());
		assertThrows (IllegalArgumentException.class, ()->anyImage.setRotation((byte)-1));
		assertThrows (IllegalArgumentException.class, ()->anyImage.setRotation((byte)4));
		anyImage.setRotation((byte)2);
		assertEquals (2, anyImage.getRotation());
		
	}
	
	@Test
	@DisplayName("Rotate mod 4")
	void rotate() {
		anyImage.setRotation((byte)2);
		assertEquals (2, anyImage.getRotation());
		assertEquals(0, anyImage.rotate((byte)2).getRotation());
		assertEquals(3, anyImage.rotate((byte)2).rotate((byte)3).rotate((byte)2).getRotation());
	}
	
	@Test
	@DisplayName("Copy copies reference to underlying image FP")
	void copyCopiesThumnailRefs() {
		FileProperty thumbnail = TestFactory.createAFileProperty(getAnyImage(), anyUser, new FileStoreRoot("/somewhere"));
		FileProperty workingImage = TestFactory.createAFileProperty(getAnyImage(), anyUser, new FileStoreRoot("/somewhere"));
       anyImage.setThumbnailImageFP(thumbnail);
       anyImage.setWorkingImageFP(workingImage);
       EcatImage copy = anyImage.copy();
       assertNotNull(copy.getWorkingImageFP());
       assertNotNull(copy.getThumbnailImageFP());
       assertEquals(thumbnail, copy.getThumbnailImageFP());
       assertEquals(workingImage, copy.getWorkingImageFP());
	}

}
