package com.researchspace.model.record;

import com.researchspace.model.User;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class FilterDeletedRecords extends RecordFilter {
	private User subject;

	public FilterDeletedRecords(User user) {
		this.subject = user;
	}

	@Override
	public boolean filter(BaseRecord toFilter) {
		return !toFilter.isDeleted() || (subject != null && !toFilter.isDeletedForUser(subject));
	}

}
