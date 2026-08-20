package com.researchspace.model.record;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;

public class BreadcrumbGeneratorTest {

	private DefaultBreadcrumbGenerator bGen;

	@Before
	public void setUp() throws Exception {
		bGen = new DefaultBreadcrumbGenerator();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGenerateBreadcrumbNoNull1stArg() {
		bGen.generateBreadcrumb(null, TestFactory.createAFolder("any", createAUser()));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGenerateBreadcrumbNoNull2ndArg() {
		bGen.generateBreadcrumb(TestFactory.createAFolder("any", createAUser()), null);
	}

	private User createAUser() {
		return TestFactory.createAnyUser("user");
	}
	
	@Test
	public void testGenerateBreadcrumbForSingleFolder() {
		User u1 = createAUser();
		Folder single = TestFactory.createAFolder("name", u1);
		single.setId(1L);
		
		Breadcrumb bcrumb = bGen.generateBreadcrumb(single, single);
		assertTrue(bcrumb.isContainLinks());
		assertEquals(1, bcrumb.getElements().size());
		assertEquals(single.getName(), bcrumb.getElements().get(0).getDisplayname());
	}
	
	@Test
	public void testGenerateBreadcrumbForNestedFolder() throws InterruptedException, IllegalAddChildOperation {
		User u1 = createAUser();
		Folder parent = TestFactory.createAFolder("parent", u1);
		Thread.sleep(1);

		Folder child = TestFactory.createAFolder("child", u1);
		parent.setId(1L);
		child.setId(2L);
		parent.addChild(child, u1, true);
		Thread.sleep(1);

		Breadcrumb bcrumb = bGen.generateBreadcrumb(child, parent);
		assertTrue(bcrumb.isContainLinks());
		assertEquals(2, bcrumb.getElements().size());

		Folder gchild = TestFactory.createAFolder("gchild", u1);
		gchild.setId(3L);
		child.addChild(gchild, u1, true);

		Breadcrumb bCrumb2 = bGen.generateBreadcrumb(gchild, parent);
		assertEquals(3, bCrumb2.getElements().size());

		Folder bothChildAndGChild = TestFactory.createAFolder("bothChildAndGChild", u1);
		bothChildAndGChild.setId(4L);
		parent.addChild(bothChildAndGChild, u1, true);
		child.addChild(bothChildAndGChild, u1, true);

		Breadcrumb bCrumb3 = bGen.generateBreadcrumb(bothChildAndGChild, parent);
		assertEquals(2, bCrumb3.getElements().size());
	}
	
	
	@Test
	public void testGenerateHomeFolderBreadcrumbs() throws InterruptedException, IllegalAddChildOperation {
		User user = createAUser();
		Folder parent = TestFactory.createAFolder("parent", user);
		parent.addType(RecordType.ROOT);
		user.setRootFolder(parent);
		Thread.sleep(1);

		Folder child = TestFactory.createAFolder("child", user);
		parent.setId(1L);
		child.setId(2L);
		parent.addChild(child, user, true);
		Thread.sleep(1);

		Breadcrumb bcrumb = bGen.generateBreadcrumbToHome(child, parent, null);
		assertTrue(bcrumb.isContainLinks());
		assertEquals(2, bcrumb.getElements().size());
		assertEquals(BreadcrumbGenerator.HOME_FOLDER_DISPLAY_NAME, bcrumb.getElements().get(0).getDisplayname());
	}

}
	
