package com.researchspace.model;

public interface IGroupNamingStrategy {
	String getSharedGroupName(Group group);

	String getSharedGroupSnippetName(Group grp);

	String getIndividualSharedFolderName(User sharer, User sharee);

	String getIndividualSharedSnippetsFolderName(User sharer, User sharee);
}
