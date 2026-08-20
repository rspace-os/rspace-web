package com.researchspace.model.views;

import java.util.EnumSet;

import com.researchspace.model.core.RecordType;

/**
 * Utility class to parameterise database searches by including or excluding
 * records by type.
 */
public class RecordTypeFilter extends AbstractEnumFilter<RecordType> {
		
	@Override
	public String toString() {
		return "RecordTypeFilter [getWantedTypes()=" + getWantedTypes() + ", getExcludedTypes()="
				+ getExcludedTypes() + "]";
	}

	public RecordTypeFilter(EnumSet<RecordType> types, boolean include) {
		super(types, include);
	}

	public RecordTypeFilter(EnumSet<RecordType> in, EnumSet<RecordType> out) {
		super(in, out);
	}

	/**
	 * Types of records we want included when searching a workspace folder
	 */
	public static final RecordTypeFilter WORKSPACE_FILTER = 
			new RecordTypeFilter(
					EnumSet.of(RecordType.FOLDER,
							RecordType.NOTEBOOK,
							RecordType.ROOT,
							RecordType.SHARED_GROUP_FOLDER_ROOT,
							RecordType.NORMAL,
							RecordType.TEMPLATE),
				// we want to exclude all these ones from the workspace display
					EnumSet.of(RecordType.MEDIA_FILE, 
							RecordType.NORMAL_EXAMPLE,
							RecordType.ROOT_MEDIA));
	
	/**
	 * Types of records we want as Move targets in Move dialog
	 */
	public static final RecordTypeFilter MOVE_DIALOGFILTER = 
			new RecordTypeFilter(
					EnumSet.of(RecordType.FOLDER,
							RecordType.ROOT,
							RecordType.SHARED_FOLDER),
					true);

	/**
	 * Types of records we want as Move targets in Move dialog
	 */
	public static final RecordTypeFilter MOVE_DIALOGFILTER_PLUS_NOTEBOOKS = 
			new RecordTypeFilter(
					EnumSet.of(RecordType.FOLDER,
							RecordType.ROOT,
							RecordType.SHARED_FOLDER,
							RecordType.NOTEBOOK),
					true);

	/**
	 * Filter for types to be displayed in the Gallery folder
	 */
	public static final RecordTypeFilter GALLERY_FILTER = 
			new RecordTypeFilter(
					EnumSet.of(RecordType.FOLDER,
							RecordType.MEDIA_FILE,
							RecordType.ROOT_MEDIA,
							RecordType.TEMPLATE,
							RecordType.SNIPPET,
							RecordType.NORMAL,
							RecordType.SHARED_GROUP_FOLDER_ROOT,
							RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT,
							RecordType.API_INBOX),
				// we want to exclude all these ones from the gallery display
					EnumSet.of(
							RecordType.NORMAL_EXAMPLE
							// removed for APiInbox
							//RecordType.SYSTEM
							));
	
}
