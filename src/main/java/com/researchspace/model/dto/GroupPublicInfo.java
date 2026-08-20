package com.researchspace.model.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.researchspace.model.Group;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GroupPublicInfo implements Serializable {

	private static final long serialVersionUID = -6707262862212582152L;

	private String displayName;
	private String uniqueName;
	private String pi;
	private List<String> otherMembers = new ArrayList<>();

	public void setOtherMembers(List<String> otherMembers) {
		this.otherMembers = otherMembers;
		if (otherMembers != null) {
			for (int i = 0; i < otherMembers.size(); i++) {
				otherMembers.set(i, otherMembers.get(i).trim());
			}
		}
	}

	public Group toGroup() {
		Group group = new Group();
		group.setDisplayName(displayName);
		group.setUniqueName(uniqueName);

		/* setting pi and members only if not blank */
		List<String> membersWithPi = new ArrayList<>();
		for (String member : otherMembers) {
			if (!StringUtils.isBlank(member)) {
				membersWithPi.add(member);
			}
		}
		if (!StringUtils.isBlank(pi)) {
			membersWithPi.add(pi);
			group.setPis(pi);
		}
		group.setMemberString(membersWithPi);

		return group;
	}
}
