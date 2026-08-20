package com.researchspace.model.views;

import java.io.Serializable;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Simple POJO for communities listing
 */
@Value
@EqualsAndHashCode(of={"id"})
public class CommunityListResult implements Serializable {
	
	private Long id;
	private String displayName;

	/**
	 * 
	 */
	private static final long serialVersionUID = 8845841673385216492L;
	
	/**
	 * Syntax for field values from autocomplete box
	 */
	public static final Pattern INPUT_VALUE_ITEM = Pattern.compile(",?\\s*(.+?)\\s*<(\\-?\\d+)>");
	public static final Pattern ALL = Pattern.compile("(,?\\s*(.+?)\\s*<(\\-?\\d+)>)+,?");
	
	public static Set<Long> getCommunityIdsfromMultiGroupAutocomplete (String input) {
		Set <Long> rc = new TreeSet<>();
		if( input == null){
			return rc;
		}
		Matcher m = INPUT_VALUE_ITEM.matcher(input.trim());
		while( m.find()){
			rc.add(Long.parseLong(m.group(2)));
		}
		return rc;	
	}
	
	public static boolean validateMultiCommunityAutocompleteInput (String input) {
		if(input == null) {
			return false;
		}
		Matcher m = ALL.matcher(input.trim());
		return m.matches();
	}




}
