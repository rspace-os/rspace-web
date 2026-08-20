package com.researchspace.model.netfiles;

/**
 * Enum representing types of external file systems that can be connected.
 */
public enum NfsClientType {

	/**
	 * samba/cifs client type
	 */
	SAMBA,
	
	/**
	 * samba2/smbj client type
	 */
	SMBJ,
	
	/**
	 * sftp client type
	 */
	SFTP,

	/**
	 * iRODS client type
	 */
	IRODS,

	/**
	 * AWS S3 client type
	 */
	S3;
	
}
