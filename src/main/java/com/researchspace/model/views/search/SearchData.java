package com.researchspace.model.views.search;

import lombok.Data;
import lombok.NonNull;

import java.util.List;

/**
 * Class to represent the json data being sent and received for searches in the workspace. Isn't
 * currently being used, but we should move to it in the future.
 */
@Data
public class SearchData {

	@NonNull
	private boolean advancedSearch;

	/**
	 * true = AND selected for queries, false = OR
	 */
	@NonNull
	private boolean fulfillAll;

	/**
	 * Name for saved searches.
	 */
	@NonNull
	private String label;

	@NonNull
	private List<SearchQuery> queries;
}
