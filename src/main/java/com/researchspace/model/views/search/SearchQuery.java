package com.researchspace.model.views.search;

import lombok.Data;
import lombok.NonNull;

/**
 * Class represents the query fields for advanced searches in JSON format.
 */
@Data
public class SearchQuery {

	@NonNull
	private SearchType searchType;

	@NonNull
	private String searchTerm;
}
