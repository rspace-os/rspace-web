package com.researchspace.service.fieldmark;

import com.researchspace.fieldmark.model.FieldmarkNotebook;
import com.researchspace.model.User;
import com.researchspace.model.dtos.fieldmark.FieldmarkNotebookDTO;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.List;
import org.springframework.web.client.HttpServerErrorException;

public interface FieldmarkServiceClientAdapter {

  List<FieldmarkNotebook> getFieldmarkNotebookList(User user)
      throws HttpServerErrorException, MalformedURLException, URISyntaxException;

  FieldmarkNotebookDTO getFieldmarkNotebook(User user, String notebookId)
      throws IOException, HttpServerErrorException;
}
