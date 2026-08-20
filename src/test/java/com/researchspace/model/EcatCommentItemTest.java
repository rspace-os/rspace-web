package com.researchspace.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EcatCommentItemTest {
	EcatCommentItem comment;
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void shallowCopyCopiesAllFieldsExceptIds() throws IllegalArgumentException, IllegalAccessException {
		comment = createAnyCommentItem();
		EcatCommentItem copy = comment.shallowCopy();
		List<Class<? super EcatCommentItem>> classes = new ArrayList<>();
		classes.add(EcatCommentItem.class);
		assertNull(copy.getComId());
	}
	
	@Test
	public void datesAreEncapsulatedAfterCopy() {
		comment = createAnyCommentItem();
		EcatCommentItem copy = comment.shallowCopy();
		copy.getCreateDate().setYear(25);
		assertFalse(comment.getCreateDate().getYear() == 25);
		
		copy.getUpdateDate().setYear(27);
		assertFalse(comment.getUpdateDate().getYear() == 27);
	}

	static  EcatCommentItem createAnyCommentItem() {
		EcatCommentItem cm1 = new EcatCommentItem();
		cm1.setCreateDate(new Date());
		cm1.setGmt_offset(1);
		cm1.setItemContent(" a comment");
		cm1.setItemName("name");
		cm1.setItemId(1L);
		cm1.setComId(1L);
		return cm1;
	}

}
