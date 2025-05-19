// eslint-disable-next-line @typescript-eslint/no-unused-vars
import tinymce, { RawEditorOptions } from "tinymce";
import React from "react";
import { Editor } from "@tinymce/tinymce-react";
import "tinymce/models/dom/model";
// Theme
import "tinymce/themes/silver";
// Toolbar icons
import "tinymce/icons/default";
// Editor styles
import "tinymce/skins/ui/oxide/skin.min.css";
// importing plugin resources
import "tinymce/plugins/autolink";
import "tinymce/plugins/charmap";
import "tinymce/plugins/link";
import "tinymce/plugins/lists";
import "tinymce/plugins/media";
import "tinymce/plugins/quickbars";
import "tinymce/plugins/autoresize";

/*
 * This is an unfortunate hack, for which there doesn't seem to be a better
 * alternative. An attempt was made to have this code run as part of the webpack
 * config, thereby ensuring that no matter where tinymce is used this setup is
 * performed. However, this resulted in the tinymce widget simply rendering as a
 * blank region of the webpack with no console errors. Not at all clear why, and
 * with little to help debug the underlying issue, this hack seems necessary.
 * As such, be careful when using tinymce in other components directly to ensure
 * that they do not depend on this component being rendered on the page to
 * correctly do this setup and also be aware that there is the possibility of
 * race conditions where this component is used multiple times on the same page.
 */
declare global {
  interface Window {
    tinymce: typeof tinymce;
  }
}
window.tinymce = tinymce;

const customStyles =
  `.mce-content-body {
    font-family: "Roboto", "Helvetica", sans-serif;
  }` +
  `.mce-content-body:not(.mce-content-readonly) {
  border-radius: 4px;
  padding: 0px 14px;
  word-break: break-word;
}` +
  `.tox-editor-header { position: static !important; }`;

export default function StyledTinyMceEditor({
  init,
  ...props
}: {
  init: RawEditorOptions;
} & React.ComponentProps<typeof Editor>): React.ReactNode {
  return (
    <Editor
      init={{
        ...init,

        // see https://www.tiny.cloud/docs/tinymce/latest/autoresize/
        autoresize: true,
        min_height: 100,
        max_height: 500,

        /*
         * This uses the default tinymce blue, which is a bit
         * darker than our primary blue. Unfortunately, the only way to change
         * this seems to be by defining a custom skin, which doesn't seem worth
         * it for such a small thing.
         * https://www.tiny.cloud/docs/tinymce/latest/content-appearance/#using-highlight_on_focus-with-custom-skins
         */
        highlight_on_focus: true,

        branding: false,
        skin: false,
        content_css: false,
        content_style: [init.content_style || "", customStyles].join("\n"),
      }}
      {...props}
    />
  );
}
