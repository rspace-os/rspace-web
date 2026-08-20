package com.researchspace.core.util;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In conjunction with URI, create, remove, and retrieve a path of folder all
 * working with canonical path, i.e without the file separator at end.
 */
public class FolderOperator {
	/**
	 * A folder added below the folder specified by the system or environment
	 * property.
	 */
	public static final String FILE_STORE_DIR_NAME = "file_store";
	private static Logger log = LoggerFactory.getLogger(FolderOperator.class);

	private File baseDir;
	private File fileRoot;

	/**
	 * Sets the filestore root based on system properties. After construction,
	 * getBaseDir and getFileRoot will be set. The folder path will be searched
	 * for in the order:
	 * <ol>
	 * <li>A path specified by system property 'RS_FILE_BASE'
	 * <li>A path specified by environment property 'RS_FILE_BASE'
	 * <li>User's home folder as a default fallback
	 * </ol>
	 */
	public FolderOperator() {
		setFileStoreRootDir(getFileStoreRoot());
	}

	private String getFileStoreRoot() {
		String rootDir = System.getProperty("RS_FILE_BASE");
		if (isEmpty(rootDir)) {
			Map<String, String> envs = System.getenv();
			if (envs != null) {
				String bs = envs.get("RS_FILE_BASE");
				if (!isEmpty(bs)) {
					rootDir = bs;
				}
			}
		}
		if (isEmpty(rootDir)) {
			rootDir = System.getProperty("user.home");
		}
		return rootDir;
	}

	public FolderOperator(String path) throws IOException {
		setFileStoreRootDir(path);
	}

	/**
	 * Gets the base folder of the file store, which will end in '/file_store/'
	 * 
	 * @return
	 */
	public File getBaseDir() {
		return baseDir;
	}

	/**
	 * Gets the filestore root as specified in system or environment property
	 * 
	 * @return
	 */
	public File getFileRoot() {
		return fileRoot;
	}

	/**
	 * Sets the root folder
	 * 
	 * @param path
	 *            such as: /Users/guangyang/myapps/workspace/akubra4u/bin
	 * @throws IllegalStateException
	 *             if teh file_store subfolder cannot be created below this
	 *             path.
	 */
	public void setFileStoreRootDir(String path) {
		fileRoot = new File(path);
		baseDir = new File(fileRoot, FILE_STORE_DIR_NAME);
		if (!baseDir.exists() && !baseDir.mkdir()) {
			log.warn("Error cannot create sub-folder: {}file_store from {}", FILE_STORE_DIR_NAME,
					fileRoot.getAbsolutePath());
			throw new IllegalStateException("Can't create subfolder of file root");
		}
	}

	/**
	 * Overloaded method to set the root dir using a {@link File}.
	 * 
	 * @param dir
	 *            a writable folder
	 */
	public void setFileStoreRootDir(File dir) {
		setFileStoreRootDir(dir.getAbsolutePath());
	}

	/**
	 * Create a whole or partially path if not exists in the FileStore
	 * 
	 * @param path
	 *            e.g. dir1/dir2/dir3,
	 * @return a File representing the created folder
	 */
	public File createPath(String path) throws IOException {
		Validate.notNull(path, "Path cannot be null, but can be empty");
		File rc = new File(baseDir, path);
		FileUtils.forceMkdir(rc);
		return rc;
	}
}
