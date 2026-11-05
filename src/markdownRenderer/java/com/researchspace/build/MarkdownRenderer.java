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

/** Release-only renderer, compiled and loaded only when htmlDocs runs. */
public final class MarkdownRenderer {
  private MarkdownRenderer() {}

  /** Renders top-level Markdown documents with the release build's existing dialect. */
  public static void render(File source, File output) throws IOException {
    DataHolder options = PegdownOptionsAdapter.flexmarkOptions(Extensions.TABLES);
    Parser parser = Parser.builder(options).build();
    HtmlRenderer renderer = HtmlRenderer.builder(options).build();
    File[] files = source.listFiles((dir, name) -> name.endsWith(".md"));
    if (files == null) {
      return;
    }
    for (File markdown : files) {
      String html =
          renderer.render(
              parser.parse(Files.readString(markdown.toPath(), StandardCharsets.UTF_8)));
      String name =
          markdown.getName().substring(0, markdown.getName().length() - ".md".length()) + ".html";
      Files.writeString(new File(output, name).toPath(), html, StandardCharsets.UTF_8);
    }
  }
}
