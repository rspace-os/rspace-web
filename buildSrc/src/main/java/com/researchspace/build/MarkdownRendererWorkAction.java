package com.researchspace.build;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

/** Calls the typed release renderer inside its isolated classloader. */
public abstract class MarkdownRendererWorkAction
    implements WorkAction<MarkdownRendererWorkAction.Parameters> {
  public interface Parameters extends WorkParameters {
    DirectoryProperty getSource();

    DirectoryProperty getOutputDir();
  }

  @Override
  public void execute() {
    try {
      Class.forName(
              "com.researchspace.build.MarkdownRenderer",
              true,
              Thread.currentThread().getContextClassLoader())
          .getMethod("render", File.class, File.class)
          .invoke(
              null,
              getParameters().getSource().get().getAsFile(),
              getParameters().getOutputDir().get().getAsFile());
    } catch (ReflectiveOperationException e) {
      Throwable cause =
          e instanceof InvocationTargetException invocation ? invocation.getCause() : e;
      throw new GradleException("Could not render release Markdown documents", cause);
    }
  }
}
