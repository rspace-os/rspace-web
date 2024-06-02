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

const customStyles =
  `.mce-content-body:not(.mce-content-readonly) {
  border-radius: 4px;
  border: 1px solid rgba(0, 0, 0, 0.23);
  padding: 0px 14px;
  word-break: break-word;
}` +
  `.mce-content-body:focus {
    outline: none !important;
    border: 2px solid #00adef;
  };` +
  `.tox-editor-header { position: static !important; }`;

export default function StyledTinyMceEditor({ init, ...props }: any): Node {
  return (
    <Editor
      init={{
        ...init,
        skin: false,
        content_css: false,
        content_style: [init.content_style || "", customStyles].join("\n"),
      }}
      {...props}
    />
  );
}
