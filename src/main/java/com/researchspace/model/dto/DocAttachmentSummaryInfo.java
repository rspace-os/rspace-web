package com.researchspace.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary information for initial attachment display - RSPAC-1700
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocAttachmentSummaryInfo {
	
	private Long id, thumbnailId, version;
	private String extension, type, name;
	
	

}
