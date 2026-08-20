package com.researchspace.model.apps;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

 class AppTest {
	
	App app;

	@Test
	 void testGetUniqueName() {
		app = createApp(App.APP_SLACK, "slack");
		assertEquals("slack", app.getUniqueName());
	}
	
	@Test
	 void isRepository() {
		app = createApp(App.APP_SLACK, "slack");
		assertFalse(app.isRepositoryApp());
		app = createApp(App.APP_DATAVERSE, "dv");
		assertTrue(app.isRepositoryApp());
		app = createApp(App.APP_FIGSHARE, "fs");
		assertTrue(app.isRepositoryApp());
	}

	@Test
	 void toIntegrationName() {
		app = createApp(App.APP_SLACK, "slack");
		assertEquals("SLACK", app.toIntegrationInfoName());
		app = createApp(App.APP_DATAVERSE, "dv");
		assertEquals("DATAVERSE", app.toIntegrationInfoName());
		app = createApp(App.APP_FIGSHARE, "fs");
		assertEquals("FIGSHARE", app.toIntegrationInfoName());
	}

	 private App createApp(String appDataverse, String dv) {
		 return new App(appDataverse, dv, false);
	 }


 }
