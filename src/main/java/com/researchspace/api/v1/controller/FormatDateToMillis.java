package com.researchspace.api.v1.controller;

import com.researchspace.core.util.DateUtil;
import java.text.SimpleDateFormat;
import org.supercsv.cellprocessor.CellProcessorAdaptor;
import org.supercsv.cellprocessor.ift.StringCellProcessor;
import org.supercsv.exception.SuperCsvCellProcessorException;
import org.supercsv.util.CsvContext;

public class FormatDateToMillis extends CellProcessorAdaptor {

  /**
   * Constructs a new <tt>FmtDate</tt> processor, which converts a date into a formatted string
   * using SimpleDateFormat, then calls the next processor in the chain.
   *
   * @param dateFormat the date format String (see {@link SimpleDateFormat})
   * @param next the next processor in the chain
   * @throws NullPointerException if dateFormat or next is null
   */
  public FormatDateToMillis(final StringCellProcessor next) {
    super(next);
  }

  public FormatDateToMillis() {
    super();
  }

  /**
   * {@inheritDoc}
   *
   * @throws SuperCsvCellProcessorException if value is null or is not a Date, or if dateFormat is
   *     not a valid date format
   */
  public Object execute(final Object value, final CsvContext context) {
    validateInputNotNull(value, context);

    if (!(value instanceof Long)) {
      throw new SuperCsvCellProcessorException(Long.class, value, context, this);
    }

    String result = DateUtil.convertDateToISOFormat(((Long) value), null);
    return next.execute(result, context);
  }
}
