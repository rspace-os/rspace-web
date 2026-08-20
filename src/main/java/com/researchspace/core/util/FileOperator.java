package com.researchspace.core.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 * Big File (>1GB) operation, add, find, update, delete, move, copy, compare,
 * search associated path operation CRD.
 */
public class FileOperator {

	private FolderOperator folderOp;

	/**
	 * Internally creates a new {@link FolderOperator} that determines the
	 * runtime FileStore root
	 */
	public FileOperator() {
		folderOp = new FolderOperator();
	}

	/**
	 * Returns the underlying {@link FolderOperator} object
	 * 
	 * @return
	 */
	public FolderOperator getFoldOp() {
		return folderOp;
	}

	/**
	 * Copies a <code>source</code>File into the file store
	 * 
	 * @param targetPath
	 *            the relative folder path within the filestore in which to put
	 *            the file
	 * @param source
	 *            The file to add
	 * @param fname
	 *            The name of the file to be put in the filestore
	 * @return A {@link URI} to the copied file.
	 * @throws IOException
	 */
	public URI addFile(String targetPath, File source, String fname) throws IOException {
		folderOp.createPath(targetPath);
		targetPath = targetPath + fname;
		File tagf = new File(folderOp.getBaseDir(), targetPath);
		copyFile(tagf, source, false);
		return tagf.toURI();
	}

	/**
	 * Copy, or move contents (delete original)
	 * 
	 * @param destination
	 *            , a new output file
	 * @param toCopy
	 *            , a input original file
	 * @param deleteToCopy
	 *            , set true delete original as move operation
	 * @throws IOException
	 */
	public void copyFile(File destination, File toCopy, boolean deleteToCopy) throws IOException {
		FileUtils.copyFile(toCopy, destination);
		if (deleteToCopy) {
			FileUtils.forceDelete(toCopy);
		}
	}

	// return how many bytes in stream. 
	public long copyStream(OutputStream outs, InputStream ins, long insSize) throws IOException {
		if(insSize > Integer.MAX_VALUE) {
			return IOUtils.copyLarge(ins, outs);
		} else {
			return IOUtils.copy(ins, outs);
		}
	}
	
	/**
	 * Delete the File from the file system.
	 *  
	 * @param file
	 * @throws IOException in case deletion was is unsuccessful
	 */
	public void deleteFile(File file) throws IOException {
		FileUtils.forceDelete(file);
	}
}
