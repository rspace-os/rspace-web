package com.researchspace.model.inventory.field;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.researchspace.core.util.JacksonUtil;

public abstract class InventoryFieldDef {
	
	/**
	 * Get list of options from internal options string retrieved from db (which is json format)
	 */
	protected List<String> getOptionListFromString(String optionsString) {
		if (StringUtils.isBlank(optionsString)) {
			return Collections.emptyList();
		}
		String[] optionsArray = JacksonUtil.fromJson(optionsString, String[].class);
		if (optionsArray == null) {
			throw new IllegalArgumentException("couldn't convert [" + optionsString + "] to options list");
		}
		return Arrays.asList(optionsArray);
	}

	/**
	 * Convert list of options into internal options string saved to db (which is json format)
	 */
	protected String getStringFromOptionList(List<String> optionsList) {
		if (CollectionUtils.isEmpty(optionsList)) {
			return "";
		}
		return JacksonUtil.toJson(optionsList);
	}

}
