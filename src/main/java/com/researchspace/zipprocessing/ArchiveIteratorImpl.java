package com.researchspace.zipprocessing;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveIteratorImpl implements ArchiveIterator {
	private Logger log = LoggerFactory.getLogger(ArchiveIteratorImpl.class);

	public void processZip(File zipArchive, ZipEntryFileProcessor entryProcessor, Predicate<ZipEntry> include)
			throws IOException, FileNotFoundException, ZipException {
		File outputDir = FileUtils.getTempDirectory();
		try (ZipFile zipFile = new ZipFile(zipArchive)) {
			String filename = RandomStringUtils.randomAlphabetic(10);
			File zipDir = new File(outputDir, filename);
			if (!zipDir.mkdir()) {
				throw new IOException("Couldn't create folder to unzip archive");
			}
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			log.info("Processing individual zip entries ...");
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();

				File entryDestination = new File(zipDir, entry.getName());
				if (!entry.isDirectory() && include.test(entry)) {
					entryDestination.getParentFile().mkdirs();
					try (InputStream in = zipFile.getInputStream(entry);
							OutputStream out = new FileOutputStream(entryDestination)) {
						IOUtils.copy(in, out);
						entryProcessor.process(entryDestination);
					}
				}
			}
		}
	}

	@Override
	public void processZipEntry(File zipArchive, ZipEntryProcessor entryProcessor, Predicate<ZipEntry> include)
			throws IOException {
		try (ZipFile zipFile = new ZipFile(zipArchive)) {

			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			log.info("Processing individual zip entries ...");
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (!entry.isDirectory() && include.test(entry)) {
					entryProcessor.process(entry);
				}
			}
		}

	}

}
