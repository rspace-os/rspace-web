package com.researchspace.core.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for high-level manipulation of Zip files
 */
public class ZipUtils {
	private static final Logger log = LoggerFactory.getLogger(ZipUtils.class);

	/**
	 * @see ZipUtils#createZip(String, File...)
	 */
	public static void createZip(File zipToCreate, File... contentRoot) throws IOException {
		createZip(zipToCreate.getAbsolutePath(), contentRoot);
	}

	/**
	 * Creates a zip file at the specified path with the contents of the
	 * specified directory. NB:
	 * 
	 * @param zipPath
	 *            The full path of the archive to create. e.g.,
	 *            c:/temp/archive.zip
	 * @param directoryPath
	 *            The path of the directory where the archive
	 *            will be created. I.e., the folder /files that contains the
	 *            content you want to zip.
	 * @throws IOException
	 *             If anything goes wrong
	 */
	public static void createZip(String zipPath, File... directoryPath) throws IOException {
		FileOutputStream fOut = null;
		BufferedOutputStream bOut = null;
		ZipArchiveOutputStream tOut = null;

		try {
			fOut = new FileOutputStream(zipPath);
			bOut = new BufferedOutputStream(fOut);
			tOut = new ZipArchiveOutputStream(bOut);
			for (File toAdd : directoryPath) {
				addFileToZip(tOut, toAdd.getAbsolutePath(), "");
			}

		} finally {
			if (tOut != null) {
				tOut.finish();
				tOut.close();
			}
			if (bOut != null) {
				bOut.close();
			}
			if (fOut != null) {
				fOut.close();
			}
		}

	}

	/**
	 * Creates a zip entry for the path specified with a name built from the
	 * base passed in and the file/directory name. If the path is a directory, a
	 * recursive call is made such that the full directory is added to the zip.
	 * 
	 * @param zOut
	 *            The zip file's output stream
	 * @param path
	 *            The filesystem path of the file/directory being added
	 * @param base
	 *            The base prefix to for the name of the zip file entry
	 * 
	 * @throws IOException
	 *             If anything goes wrong
	 */
	private static void addFileToZip(ZipArchiveOutputStream zOut, String path, String base) throws IOException {
		File f = new File(path);
		String entryName = base + f.getName();
		ZipArchiveEntry zipEntry = new ZipArchiveEntry(f, entryName);

		zOut.putArchiveEntry(zipEntry);

		if (f.isFile()) {
			try (FileInputStream fInputStream = new FileInputStream(f)) {
				IOUtils.copy(fInputStream, zOut);
				zOut.closeArchiveEntry();
			}

		} else {
			zOut.closeArchiveEntry();
			File[] children = f.listFiles();
			if (children != null) {
				for (File child : children) {
					addFileToZip(zOut, child.getAbsolutePath(), entryName + "/");
				}
			}
		}
	}

	/**
	 * Extract zip file at the specified destination path. NB:archive must
	 * consist of a single root folder containing everything else.
	 * 
	 * @param archivePath
	 *            path to zip file, e.g., a/b/c/d.archive.zip
	 * @param destinationPath
	 *            The folder into which the zip archive will be extracted.
	 */
	public static String extractZip(String archivePath, String destinationPath) throws IOException {
		return extractZip(new File(archivePath), new File(destinationPath));
	}

	/**
	 * Overloaded method taking files as arguments.
	 * 
	 * @param archiveFile
	 * @param destinationFolder
	 *            the folder into which zip contents should be put.
	 * @return The name of top-level folder or <code>null</code> if no top-level
	 *         folder in zip
	 */
	public static String extractZip(File archiveFile, File destinationFolder) throws IOException {
		if (!destinationFolder.exists()) {
			boolean dirMade = destinationFolder.mkdir();
			if (!dirMade) {
				throw new IllegalStateException("Couldn't make folder to unzip into");
			}
		}
		return unzipFolder(archiveFile, destinationFolder);
	}

	/**
	 * Unzips a zip file into the given destination directory.
	 * 
	 * The archive file MUST have a unique "root" folder. This root folder is
	 * skipped when unarchiving.
	 * 
	 * @return The name of top-level folder or <code>null</code> if no top-level
	 *         folder
	 */
	private static String unzipFolder(File archiveFile, File zipDestinationFolder) throws IOException {

		final int bufferSize = 65536;
		ZipFile zipFile = null;
		File topLevelDir = null;
		try {
			zipFile = new ZipFile(archiveFile);
			byte[] buf = new byte[bufferSize];

			Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
			while (entries.hasMoreElements()) {
				ZipArchiveEntry zipEntry = entries.nextElement();
				String name = zipEntry.getName();
				name = name.replace('\\', '/');

				File destinationFile = new File(zipDestinationFolder, name);
				if (name.endsWith("/")) {
					if (!destinationFile.isDirectory() && !destinationFile.mkdirs()) {
						log.warn("Error creating temp directory:{}", destinationFile.getPath());
						return destinationFile.getAbsolutePath();
					}
					if (topLevelDir == null) {
						topLevelDir = destinationFile;
					}
					continue;
				}

				try (FileOutputStream fos = new FileOutputStream(destinationFile)) {
					int n;
					InputStream entryContent = zipFile.getInputStream(zipEntry);
					while ((n = entryContent.read(buf)) != -1) {
						if (n > 0) {
							fos.write(buf, 0, n);
						}
					}
				}
			}

		} catch (IOException e) {
			log.warn("Unzip failed:" + e.getMessage());
			throw e;
		} finally {
			IOUtils.closeQuietly(zipFile);
		}

		return (topLevelDir != null) ? topLevelDir.getAbsolutePath() : null;
	}

}
