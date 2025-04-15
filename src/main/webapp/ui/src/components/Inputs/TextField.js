//@flow
import React, { type Node } from "react";
import Editor from "./StyledTinyMceEditor";
import NoValue from "../../components/NoValue";
import DOMPurify from "dompurify";

export type TextFieldArgs = {|
  // required
  value: string,

  // optional
  disabled?: boolean,
  name?: string,
  noValueLabel?: ?string,
  onChange?: ({ target: { name: string, value: string } }) => void,
  variant?: "filled" | "outlined" | "standard",
  id?: string,
|};

export default function TextField(props: TextFieldArgs): Node {
  const handleEditorChange = (content: string) => {
    const e = {
      target: {
        name: props.name ?? "",
        value: content,
      },
    };
    if (props.onChange) props.onChange(e);
  };

  if (props.disabled) {
    if (props.value) {
      return (
        <span
          dangerouslySetInnerHTML={{
            __html: DOMPurify.sanitize(props.value, { ADD_ATTR: ["target"] }),
          }}
        ></span>
      );
    }
    return <NoValue label={props.noValueLabel ?? "None"} />;
  }

  const handleEditorInit = (editor) => {
    editor.ui.registry.addAutocompleter('custom_autocompleter', {
      ch: '/',
      minChars: 0,
      columns: 1,
      fetch: (pattern) => {
        const items = [
          { text: 'Option 1', value: 'value1' },
          { text: 'Option 2', value: 'value2' },
        ];
        return new Promise((resolve) => {
          resolve(items.filter(item => item.text.indexOf(pattern) !== -1));
        });
      },
      onAction: (autocompleteApi, rng, value) => {
        editor.selection.setRng(rng);
        editor.insertContent(value);
        autocompleteApi.hide();
      }
    });
    editor.ui.registry.addButton("pyrat", {
      tooltip: "Link to PyRAT",
      icon: "pyrat",
      onAction() {
        alert("open pyrat dialog");
      },
    });
    editor.ui.registry.addMenuItem("optPyrat", {
      text: "From PyRAT",
      icon: "pyrat",
      onAction() {
        alert("open pyrat dialog");
      },
    });
  };

  return (
    <Editor
      {...props}
      onChange={null}
      onEditorChange={handleEditorChange}
      init={{
        menu: {
          insert: { title: 'Insert', items: 'link | charmap hr | optPyrat' },
        },
        plugins: ["autolink", "lists", "link", "charmap", "autoresize", "autocompleter", "pyrat"],
        toolbar:
          "undo redo blocks bold italic backcolor link \
           bullist numlist removeformat \
           pyrat",
        paste_block_drop: true,
        setup: handleEditorInit,
      }}
    />
  );
}
