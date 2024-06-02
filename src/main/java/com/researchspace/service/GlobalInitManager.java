package com.researchspace.service;

import com.researchspace.model.record.IllegalAddChildOperation;
import java.util.List;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;

/** Performs global initialisation actions on application startup or refresh */
public interface GlobalInitManager extends ApplicationListener {

  /**
   * @param applicationContext ApplicationContext event indicating reloading of context
   * @throws IllegalAddChildOperation
   */
  void globalInit(ApplicationContext applicationContext) throws IllegalAddChildOperation;

  List<IApplicationInitialisor> getApplicationInitialisors();
}
