// @flow

// eslint-disable-next-line no-unused-vars
import tinymce from "tinymce";
import React, { type Node } from "react";
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

export default function StyledTinyMceEditor({ init, ...props }: any): Node {
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
