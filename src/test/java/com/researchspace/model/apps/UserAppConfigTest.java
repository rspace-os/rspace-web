
package com.researchspace.model.apps;

import static com.researchspace.core.util.TransformerUtils.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.stream.LongStream;

import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.system.SystemPropertyTestFactory;

public class UserAppConfigTest {
	
	User user;
	App app;

	@BeforeEach
	public void setUp() throws Exception {
		user = TestFactory.createAnyUser("any");
		app = SystemPropertyTestFactory.createAnyApp();
	}
	
	@Test
	public void addAppConfigForUserThrowsIAEForEmptySet() {
		UserAppConfig cfg = new UserAppConfig(1L, user, app, true);
		AppConfigElementSet toAdd = new AppConfigElementSet();
		assertThrows(IllegalArgumentException.class, ()->cfg.addConfigSet(toAdd));
	}

	private void addTwoConfigSets(UserAppConfig cfg) {
		LongStream.of(1, 2).forEach( id -> {
		   AppConfigElementSet toAdd = new AppConfigElementSet();
		   toAdd.setId(id);
		   toAdd.setConfigElements(toSet(new AppConfigElement()));
		   cfg.addConfigSet(toAdd);
		 });
	}
	
	@Test
	public void addAppAndRemoveConfigsForUser() {
		UserAppConfig cfg = new UserAppConfig(1L, user, app, true);
		addTwoConfigSets(cfg);
		assertEquals (2, cfg.getConfigElementSetCount());	
		cfg.removeConfigSet(cfg.getAppConfigElementSets().iterator().next());
		assertEquals (1, cfg.getConfigElementSetCount());	
		cfg.clear();		
		Assertions.assertEquals ( 0, cfg.getConfigElementSetCount(),"Clear didn't remove all elements");
	}
}
