package com.researchspace.model.netfiles;

/**
 * Enum representing option keys used in NfsFileSystem options maps.
 */
public enum NfsFileSystemOption {
	
	/**
	 * samba domain
	 */
	SAMBA_DOMAIN,

	/**
	 * samba share name
	 */
	SAMBA_SHARE_NAME,

	/**
	 * sftp server public key
	 */
	SFTP_SERVER_PUBLIC_KEY,
	
	/**
	 * for public key authentication, points to URL for key registration 
	 */
	PUBLIC_KEY_REGISTRATION_DIALOG_URL,
	/**
	 * users of this file system must specify a root directory when they login
	 */
	USER_DIRS_REQUIRED,

	/**
	 * iRODS Zone name
	 */
	IRODS_ZONE,

	/**
	 * iRODS Home Directory
	 */
	IRODS_HOME_DIR,

	/**
	 * iRODS port
	 */
	IRODS_PORT,
	
	/**
	 * iRODS auth method
	 */
	IRODS_AUTH,

	/**
	 * iRODS CS negotiation policy
	 */
	IRODS_CSNEG,

	/**
	 * AWS S3 region
	 */
	S3_REGION,

	/**
	 * Name of the AWS S3 bucket
	 */
	S3_BUCKET_NAME,

	/**
	 * Whether S3 client should support pathStyleAccessEnabled option (true/false)
	 */
	S3_PATH_STYLE_ACCESS_ENABLED

}
