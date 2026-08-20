package com.researchspace.model.comms.data;

import java.util.List;
import java.util.Set;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=false)
public class ArchiveExportNotificationData extends NotificationData {

	private String downloadLink;
	
	private String archiveType;
	private String exportScope;
	private String exportedUserOrGroupId;

	private boolean nfsLinksIncluded;
	private Long maxNfsFileSize; 
	private Set<String> excludedNfsFileExtensions;
	
	private List<ExportedRecordData> exportedRecords;
	private List<ExportedNfsLinkData> exportedNfsLinks;

	@Data
	public static class ExportedRecordData {
		private String globalId;
		private String name;

		/* if parent (folder/notebook) was included in export, here is its global id */
		private String exportedParentGlobalId; 
	}
	
	@Data
	public static class ExportedNfsLinkData {
		private String fileSystemName;
		private String fileStorePath;
		private String relativePath;
	 
		private boolean addedToArchive;
		private String errorMsg;
		
		private boolean folderLink;
		private String folderExportSummaryMsg;
	}
	
}
