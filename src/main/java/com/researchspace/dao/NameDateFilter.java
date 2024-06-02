package com.researchspace.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.record.BaseRecord;

public interface NameDateFilter {

  ISearchResults<BaseRecord> match(WorkspaceListingConfig input);
}
