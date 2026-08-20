package com.researchspace.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DefaultGroupNamingStrategy implements IGroupNamingStrategy {
	@Override
	public String getSharedGroupName(Group grp) {
		String sharedGrpname = (grp.getDisplayName() != null ? grp.getDisplayName() : grp.getUniqueName());
		return sharedGrpname + "_SHARED";
	}
	@Override
	public String getSharedGroupSnippetName(Group grp) {
		String sharedGrpname = (grp.getDisplayName() != null ? grp.getDisplayName() : grp.getUniqueName());
		return sharedGrpname + "_SHARED_SNIPPETS";
	}

	/**
	 * Gets a reproducible name for the individual shared folder based on the
	 * users' names.
	 */
	@Override
	public String getIndividualSharedFolderName(User sharer, User sharee) {
		List<User> users = Arrays.asList(new User[] { sharer, sharee });
		Collections.sort(users);
		return users.get(0).getUsername() + "-" + users.get(1).getUsername();
	}

	@Override
	public String getIndividualSharedSnippetsFolderName(User sharer, User sharee) {
		return getIndividualSharedFolderName(sharer, sharee) + "_SNIPPETS";
	}

}
