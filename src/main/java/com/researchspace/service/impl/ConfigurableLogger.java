package com.researchspace.service.impl;

import org.slf4j.Logger;

public interface ConfigurableLogger {

  Logger setLoggerDefault();

  void setLogger(Logger log);
}
