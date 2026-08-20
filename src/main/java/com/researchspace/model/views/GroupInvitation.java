package com.researchspace.model.views;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class GroupInvitation {
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Invitee {
		private String email;
		private Long recipientId;
	}
	Long requestId;
	List<Invitee> recipients;

}
