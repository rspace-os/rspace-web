package com.researchspace.model.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public  class MiniProfile {
	private String email, fullname, username, profileImageLink;
	@JsonSerialize(using = ISO8601DateTimeSerialiser.class)
	@JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
	private Long lastLogin;
	private boolean accountEnabled;
	@Builder.Default
	private List<GroupInfo> groups = new ArrayList<>();
}


