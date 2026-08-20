package com.researchspace.model;

import static com.researchspace.model.RSpaceModelTestUtils.getResource;
import static com.researchspace.model.record.TestFactory.createAFileProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.researchspace.core.testutil.ModelTestUtils;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.Thumbnail.SourceType;
import com.researchspace.model.record.TestFactory;




public class ThumbnailTest {
	
	public static String thumbURL ="/thumbnail/data?sourceType=IMAGE&sourceId=16342&sourceParentId=12877825&width=644&height=328&rotation=3&time=1406822005870";
	public static String invalidRotation ="/thumbnail/data?sourceType=IMAGE&sourceId=16342&sourceParentId=12877825&width=644&height=328&rotation=22&time=1406822005870";
	public static String chemthumbURL ="/thumbnail/data?sourceType=CHEM&sourceId=16342&sourceParentId=12877825&width=644&height=328&time=1406822005870";

	Thumbnail thumbnail;
	User anyUser = null;
	@BeforeEach
	public  void setUp() throws Exception {
		thumbnail = new Thumbnail();
		anyUser = TestFactory.createAnyUser("any");
	}

	@Test
	public void setWidthSetsMaxHeight() {
		thumbnail.setHeight(Thumbnail.MAX_THUMBNAIL_SIZE + 1);
		assertEquals(Thumbnail.MAX_THUMBNAIL_SIZE, thumbnail.getHeight());
	}

	@Test
	public void setHeightSetsMaxWidth() {
		thumbnail.setWidth(Thumbnail.MAX_THUMBNAIL_SIZE + 1);
		assertEquals(Thumbnail.MAX_THUMBNAIL_SIZE, thumbnail.getWidth());
	}
	
	@Test
	public void copy() throws Exception {
		thumbnail = TestFactory.createThumbnail(10, 20);
		thumbnail.setId(1L);
		FileProperty fp = createAFileProperty(getResource("Picture1.png"), 
				anyUser, new FileStoreRoot());
		thumbnail.setThumbnailFP(fp);
		Thumbnail copy = thumbnail.getCopy();
		assertEquals(copy, thumbnail);
		assertNull(copy.getId());
		List<Class<? super Thumbnail>> classes = new ArrayList<>();
		classes.add(Thumbnail.class);
		ModelTestUtils.assertCopiedFieldsAreEqual(copy, thumbnail, TransformerUtils.toSet("id"),
				 classes);
		assertTrue(thumbnail.isImageThumbnail());
		assertTrue(copy.isImageThumbnail());
		assertEquals(fp, copy.getThumbnailFP());
	}
	
	@Test
	public void testFromChemURL() {
		Thumbnail fromURL = Thumbnail.fromURL(chemthumbURL);
		assertFalse(fromURL.isImageThumbnail());
		assertTrue(fromURL.isChemThumbnail());
	}

	@Test
	public void fromURL()  {
		Thumbnail fromURL = Thumbnail.fromURL(thumbURL);
		assertEquals(16342, fromURL.getSourceId().intValue());
		assertEquals(SourceType.IMAGE, fromURL.getSourceType());
		assertEquals(12877825, fromURL.getSourceParentId().intValue());
		assertEquals(644, fromURL.getWidth());
		assertEquals(328, fromURL.getHeight());
		assertEquals(3, fromURL.getRotation());
	}
	
	@Test
	public void fromURLInvalidRotationIsIgnored()  {
		Thumbnail fromURL = Thumbnail.fromURL(invalidRotation);
		assertEquals(0, fromURL.getRotation());
	}
}
