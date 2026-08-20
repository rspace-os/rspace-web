package com.researchspace.model.netfiles;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.apache.commons.io.FilenameUtils;

import lombok.Value;
/**
 * View only representation of the path elements that define the full path to an Nfs resource.
 */
@Value
public class NfsPathElements {
  private String fileSystemPath;
  private String fileStorePath;
  private String relativeFilePath;
  
  public String toFullPath () {
	  if(isBlank(fileSystemPath) && isBlank(fileStorePath) && isBlank(relativeFilePath) ) {
		  return "";
	  }
	  StringBuilder builder = new StringBuilder();
	  String path = fileStorePath + "/" + relativeFilePath;
	  // remove double // or more 
	  path = path.replaceAll("/{2,}", "/");
	  String normalisedPath = FilenameUtils.normalize(path);
	  // can't normalise file system paths as can be strange, e.g. 'sg.datastore.ed.ac.uk:22222'
	  return builder.append(fileSystemPath).append(fileSystemPath.endsWith("/")?"":"/")
			  .append(normalisedPath.startsWith("/")?normalisedPath.replaceFirst("/",	 ""):normalisedPath)
			  .toString();
  }
}
