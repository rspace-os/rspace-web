package com.researchspace.api.v1.controller;

import com.researchspace.apiutils.ApiError;
import com.researchspace.webapp.config.WebConfig;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.supercsv.cellprocessor.CellProcessorAdaptor;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.prefs.CsvPreference;
import org.supercsv.util.CsvContext;

/**
 * MessageConverter that converts ApiError to CSV format for API calls that are expected to respond
 * in CSV format. <br>
 * This converter is registered in {@link WebConfig} <br>
 * Currently this only supports conversion of ApiError objects.
 */
public class CSVApiErrorMessageConverter extends AbstractHttpMessageConverter<ApiError> {
  private static final String TEXT_CSV = "text/csv";
  private static final String[] HEADER = {
    "status", "httpCode", "internalCode", "message", "errors"
  };

  private static class FmtEnumName extends CellProcessorAdaptor {
    @Override
    public String execute(Object o, CsvContext csvContext) {
      return ((Enum) o).name();
    }
  }

  private static final CellProcessor[] CELL_PROCESSORS =
      new CellProcessor[] {
        new FmtEnumName(), new Optional(), new Optional(), new Optional(), new Optional()
      };

  public CSVApiErrorMessageConverter() {
    super(MediaType.valueOf(TEXT_CSV));
  }

  @Override
  protected boolean supports(Class<?> aClass) {
    return ApiError.class.isAssignableFrom(aClass);
  }

  @Override
  protected ApiError readInternal(
      Class<? extends ApiError> aClass, HttpInputMessage httpInputMessage)
      throws IOException, HttpMessageNotReadableException {
    throw new UnsupportedOperationException("readInternal() is not supported yet");
  }

  @Override
  protected void writeInternal(ApiError apiError, HttpOutputMessage httpOutputMessage)
      throws IOException, HttpMessageNotWritableException {
    httpOutputMessage.getHeaders().add(HttpHeaders.CONTENT_TYPE, TEXT_CSV);

    try (final Writer writer =
        new OutputStreamWriter(httpOutputMessage.getBody(), StandardCharsets.UTF_8)) {
      final CsvBeanWriter beanWriter = new CsvBeanWriter(writer, CsvPreference.STANDARD_PREFERENCE);
      beanWriter.writeHeader(HEADER);
      beanWriter.write(apiError, HEADER, CELL_PROCESSORS);
      beanWriter.flush();
    }
  }
}
