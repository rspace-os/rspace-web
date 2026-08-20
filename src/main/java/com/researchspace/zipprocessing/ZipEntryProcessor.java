package com.researchspace.zipprocessing;

import java.io.File;
import java.util.zip.ZipEntry;

/**
 * Processes a single Zip entry that is available as a {@link File}.
 */
@FunctionalInterface
public interface ZipEntryProcessor {

	void process(ZipEntry entryAsFile);

}
