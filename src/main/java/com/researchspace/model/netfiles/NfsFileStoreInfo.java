package com.researchspace.model.netfiles;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 
 * DTO object for NfsFileStore information that user may want to see in the UI. 
 */

@Data
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
public class NfsFileStoreInfo implements Serializable {

	private static final long serialVersionUID = 2049454683874806673L;

	private Long id;
	private String name;
	private String path;

	private NfsFileSystemInfo fileSystem;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private NfsUserPermissions userPermissions;

	public NfsFileStoreInfo(NfsFileStore nfsFileStore) {
		NfsFileSystem fileSystem = nfsFileStore.getFileSystem();
		if (fileSystem != null) {
			this.fileSystem = fileSystem.toFileSystemInfo(); 
		}
		
		id = nfsFileStore.getId();
		name = nfsFileStore.getName();
		path = nfsFileStore.getPath();
	}

}
