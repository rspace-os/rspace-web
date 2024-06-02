package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiDocument;
import com.researchspace.api.v1.model.ApiDocumentField;
import com.researchspace.webapp.config.WebConfig;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.prefs.CsvPreference;

/**
 * Delegates to SuperCSV CSV generator. <br>
 * This converter is registered in {@link WebConfig} <br>
 * Currently this onlys supports conversion of certain objects.
 */
public class SuperCSVMessageConverter extends AbstractHttpMessageConverter<Object> {

  //	static final String ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
  // these will be column names to be writtne out in CSV format
  private static final String[] HEADER = {
    "id", "globalId", "name", "type", "lastModified", "content"
  };
  private static final String[] FIELD_NAMES = {
    "id", "globalId", "name", "type", "lastModifiedMillis", "content"
  };
  private static final CellProcessor[] CELL_PROCESSORS =
      new CellProcessor[] {
        new NotNull(),
        new NotNull(),
        new NotNull(),
        new NotNull(),
        new FormatDateToMillis(),
        new Optional()
      };
  private static final String TEXT_CSV = "text/csv";

  public SuperCSVMessageConverter() {
    super(MediaType.valueOf(TEXT_CSV));
  }

  @Override
  protected boolean supports(Class<?> aClass) {
    return ApiDocument.class.isAssignableFrom(aClass);
  }

  @Override
  protected ApiDocument readInternal(Class<?> aClass, HttpInputMessage message)
      throws IOException, HttpMessageNotReadableException {
    throw new UnsupportedOperationException("readInternal() is not supported yet");
  }

  @Override
  protected void writeInternal(Object object, HttpOutputMessage message)
      throws IOException, HttpMessageNotWritableException {
    message.getHeaders().add(HttpHeaders.CONTENT_TYPE, TEXT_CSV);
    try (final Writer writer = new OutputStreamWriter(message.getBody(), StandardCharsets.UTF_8)) {
      final CsvBeanWriter beanWriter = new CsvBeanWriter(writer, CsvPreference.STANDARD_PREFERENCE);
      beanWriter.writeHeader(HEADER);
      ApiDocument doc = (ApiDocument) object;
      List<ApiDocumentField> fields = doc.getFields();
      writeItems(beanWriter, fields, FIELD_NAMES);
      beanWriter.flush();
    }
  }

  // writes out each row
  private void writeItems(CsvBeanWriter beanWriter, List<ApiDocumentField> fields, String[] header)
      throws IOException {
    for (ApiDocumentField field : fields) {
      beanWriter.write(field, header, CELL_PROCESSORS);
    }
  }
}
