package com.researchspace.model.record;

import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.model.User;


public class CycleAnalyzerTest {

	User u = TestFactory.createAnyUser("any");
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() throws InterruptedException, IllegalAddChildOperation {
		
		Folder f1 = TestFactory.createAFolder("f1", u);
		Thread.sleep(1);
		Folder f2 = TestFactory.createAFolder("f2", u);
		Thread.sleep(1);
		Folder f3 = TestFactory.createAFolder("f3", u);
		Thread.sleep(1);
		Folder f4 = TestFactory.createAFolder("f4", u);
		Thread.sleep(1);
		Folder f5 = TestFactory.createAFolder("f5", u);
		Thread.sleep(1);
		Folder f6 = TestFactory.createAFolder("f6", u);
		Thread.sleep(1);
		Folder f7 = TestFactory.createAFolder("f7", u);
		Thread.sleep(1);
		//f1->f2->f3,f1->f4
		f1.doAddToParentsOnly(f2, u);
		f1.doAddToParentsOnly(f4, u);
		f2.doAddToParentsOnly(f3, u);

		f7.doAddToParentsOnly(f1, u);
		f3.doAddToParentsOnly(f7,u);
		boolean addexception =false;
		try {
			f1.addChild(f6, u, true);
		} catch (IllegalAddChildOperation e) {
			addexception = true;
		}
		if (!addexception) {
			fail();
		}
	}


}
