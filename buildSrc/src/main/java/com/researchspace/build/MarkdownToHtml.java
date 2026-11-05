package com.researchspace.build;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

/**
 * Renders each {@code *.md} file at the top of a directory to an HTML fragment of the same name,
 * with pegdown's Markdown dialect and its tables extension, as markdown-page-generator-plugin did.
 */
@CacheableTask
public abstract class MarkdownToHtml extends DefaultTask {

  @InputDirectory
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract DirectoryProperty getSource();

  @OutputDirectory
  public abstract DirectoryProperty getOutputDir();

  @Classpath
  public abstract ConfigurableFileCollection getRendererClasspath();

  @javax.inject.Inject
  protected abstract FileSystemOperations getFs();

  @javax.inject.Inject
  protected abstract WorkerExecutor getWorkerExecutor();

  @TaskAction
  public void render() throws IOException {
    File out = getOutputDir().get().getAsFile();
    getFs().delete(spec -> spec.delete(out));
    Files.createDirectories(out.toPath());

    WorkQueue queue =
        getWorkerExecutor()
            .classLoaderIsolation(spec -> spec.getClasspath().from(getRendererClasspath()));
    queue.submit(
        MarkdownRendererWorkAction.class,
        parameters -> {
          parameters.getSource().set(getSource());
          parameters.getOutputDir().set(getOutputDir());
        });
    queue.await();
  }
}
