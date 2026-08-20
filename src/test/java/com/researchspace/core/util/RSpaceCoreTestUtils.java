package com.researchspace.core.util;

import com.researchspace.core.testutil.Invokable;
import org.apache.commons.io.IOUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;

public class RSpaceCoreTestUtils {

	/**
	 * Gets a named  resource in src/test/resources/, as a byte array.
	 * @param fileName
	 * @return
	 * @throws IOException
	 */
	public static byte [] getResourceAsByteArray(String fileName) throws IOException {
		return IOUtils.toByteArray(getInputStreamOnFromTestResourcesFolder(fileName));
	}

	/**
	 * Given the name of a  file in src/test/resources/, returns
	 *  an Input stream to it. CLient should close the input stream.
	 * @param fileName
	 * @return
	 * @throws IOException
	 */
	public static InputStream getInputStreamOnFromTestResourcesFolder(String fileName) throws IOException {
		InputStream is = RSpaceCoreTestUtils.class.getClassLoader().getResourceAsStream(fileName);
		return is;
	}

	/**
	 * Gets a test file, specified by its name relative to TestResources folder.
	 * @param fileName
	 * @return
	 */
	 public static File getResource(String fileName) {
		File resource = new File("src/test/resources/" + fileName);
		return resource;
	}

}
