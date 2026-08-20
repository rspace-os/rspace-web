package com.researchspace.zipprocessing;

import java.io.File;
import java.io.IOException;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;

/**
 * Utility to iterate over a Zip file, extract entries as files, and process
 * them using a {@link ZipEntryFileProcessor} if the <code>include</code>
 * {@link Predicate} returns <code>true</code>.
 */
public interface ArchiveIterator {

	/**
	 * Processes a zip archive
	 * 
	 * @param zipArchive
	 *            A Zip file
	 * @param entryProcessor
	 *            Does something to a single {@link ZipEntry}
	 * @param include
	 *            a {@link Predicate} to determine if the
	 *            {@link ZipEntryFileProcessor} should process a given
	 *            {@link ZipEntry}. If <code>include</code> returns
	 *            <code>true</code> then it will be processed.
	 * 
	 * @throws IOException
	 */
	void processZip(File zipArchive, ZipEntryFileProcessor entryProcessor, Predicate<ZipEntry> include) throws IOException;

	void processZipEntry(File zipArchive, ZipEntryProcessor entryProcessor, Predicate<ZipEntry> include) throws IOException;
	
}
