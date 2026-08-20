package com.researchspace.model.record;

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DocumentInitializationPolicyTest {
	
	class DocumentInitializationPolicyTestSpy extends DocumentInitializationPolicy {
        boolean isInitialised = false;
		@Override
		protected void doInitialize(BaseRecord baseRecord) {
			isInitialised = true;	
		}	
	}
	
	class DocumentInitializationPolicy2TestSpy extends DocumentInitializationPolicy {
        boolean isInitialised = false;
        public DocumentInitializationPolicy2TestSpy(DocumentInitializationPolicy decorator) {
    		super(decorator);
    	}
		@Override
		protected void doInitialize(BaseRecord baseRecord) {
			isInitialised = true;	
		}	
	}

	DocumentInitializationPolicyTestSpy spy; 
	@Before
	public void setUp() throws Exception {
		spy = new DocumentInitializationPolicyTestSpy();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testNullPolicyHandled() {
		spy.initialize(TestFactory.createAnySD());
		assertTrue(spy.isInitialised);
	}
	
	@Test
	public void testNestedPolicyHandled() {
		DocumentInitializationPolicy2TestSpy  spy2 = new DocumentInitializationPolicy2TestSpy (spy);
		spy2.initialize(TestFactory.createAnySD());
		assertTrue(spy.isInitialised);
		assertTrue(spy2.isInitialised);
	}

}
