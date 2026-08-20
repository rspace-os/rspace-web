package com.researchspace.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.researchspace.core.testutil.ModelTestUtils;

public class EcatCommentTest {
	
	EcatComment comment;
	
	@Test
	public void testShallowCopy() throws IllegalArgumentException, IllegalAccessException {
		comment = createAnyComment();
		EcatComment copy = comment.shallowCopy();
		Set<String> toExclude = ModelTestUtils.generateExclusionFieldsFrom("comId");
		List<Class<? super EcatComment>> classes = new ArrayList<>();
		classes.add(EcatComment.class);
		ModelTestUtils.assertCopiedFieldsAreEqual(copy, comment, toExclude, classes);
		assertNull(copy.getComId());
	}
	
	@Test
	public void datesAreEncapsulatedAfterCopy() {
		comment = createAnyComment();
		EcatComment copy = comment.shallowCopy();
		copy.getCreateDate().setYear(25);
		assertFalse(comment.getCreateDate().getYear() == 25);
		
		copy.getUpdateDate().setYear(27);
		assertFalse(comment.getUpdateDate().getYear() == 27);
	}

	@Test
	 public void getCopyWithCopiedCommentItemsTest() {
		EcatComment cmmnt = createAnyComment();
		EcatCommentItem item = EcatCommentItemTest.createAnyCommentItem();
		
		//check basic add
		cmmnt.addCommentItem(item);
		assertNotNull(item.getEcatComment());
		assertEquals(1, cmmnt.getItems().size());
		// now do full copy and ensure relationships are separate
		EcatComment fullCopy =cmmnt.getCopyWithCopiedCommentItems();
		assertEquals(1,fullCopy.getItems().size());
		EcatCommentItem copiedItem = fullCopy.getItems().get(0);
		assertEquals(fullCopy, copiedItem.getEcatComment());
	}

	private EcatComment createAnyComment() {
		EcatComment cm1 = new EcatComment();
		cm1.setComName("image1");
		cm1.setAuthor("someone1");
		cm1.setComDesc("sesc");
		cm1.setSequence(0);
		cm1.setComId(1L);
		return cm1;
	}

}
