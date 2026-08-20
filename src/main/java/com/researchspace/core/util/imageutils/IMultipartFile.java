package com.researchspace.core.util.imageutils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Abstraction over Spring's multipart file to avoid dependency on spring mvc in this project
 */
public interface IMultipartFile {

	String getOriginalFilename();

	void transferTo(File tempTiff) throws IOException;

	InputStream getInputStream() throws  IOException;

}
