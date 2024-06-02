package com.researchspace.api.v1.service.impl;

import com.researchspace.core.util.imageutils.IMultipartFile;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import lombok.Value;
import org.springframework.web.multipart.MultipartFile;

@Value
public class SpringMultipartFileAdapter implements IMultipartFile {
  MultipartFile file;

  @Override
  public String getOriginalFilename() {
    return file.getOriginalFilename();
  }

  @Override
  public void transferTo(File tempTiff) throws IOException {
    file.transferTo(tempTiff);
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return file.getInputStream();
  }
}
