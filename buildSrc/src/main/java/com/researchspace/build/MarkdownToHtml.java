package com.researchspace.build;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.profile.pegdown.Extensions;
import com.vladsch.flexmark.profile.pegdown.PegdownOptionsAdapter;
import com.vladsch.flexmark.util.data.DataHolder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

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

  @javax.inject.Inject
  protected abstract FileSystemOperations getFs();

  @TaskAction
  public void render() throws IOException {
    File out = getOutputDir().get().getAsFile();
    getFs().delete(spec -> spec.delete(out));
    Files.createDirectories(out.toPath());
    DataHolder options = PegdownOptionsAdapter.flexmarkOptions(Extensions.TABLES);
    Parser parser = Parser.builder(options).build();
    HtmlRenderer renderer = HtmlRenderer.builder(options).build();
    File[] files = getSource().get().getAsFile().listFiles((dir, name) -> name.endsWith(".md"));
    if (files == null) {
      return;
    }
    for (File md : files) {
      String html =
          renderer.render(parser.parse(Files.readString(md.toPath(), StandardCharsets.UTF_8)));
      String name = md.getName().substring(0, md.getName().length() - ".md".length()) + ".html";
      Files.writeString(new File(out, name).toPath(), html, StandardCharsets.UTF_8);
    }
  }
}
