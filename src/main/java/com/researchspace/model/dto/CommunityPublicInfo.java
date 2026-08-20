package com.researchspace.model.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.researchspace.model.Community;

import lombok.Data;

@Data
public class CommunityPublicInfo implements Serializable {

	private static final long serialVersionUID = 6293708029829021087L;

	private String displayName;
	private String uniqueName;
	private List<String> labGroups = new ArrayList<>();
	private List<String> admins = new ArrayList<>();

	/**
	 * Doesn't set lab groups nor community admins
	 * 
	 * @return
	 */
	public Community toCommunity() {
		Community community = new Community();
		community.setDisplayName(getDisplayName());
		community.setUniqueName(getUniqueName());
		community.createAndSetUniqueName();

		return community;
	}

}
