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
    let e = {
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
    } else {
      return <NoValue label={props.noValueLabel ?? "None"} />;
    }
  } else {
    return (
      <Editor
        {...props}
        onChange={null}
        onEditorChange={handleEditorChange}
        inline
        init={{
          menubar: false,
          plugins: ["autolink", "lists", "link", "charmap"],
          toolbar:
            "undo redo | blocks | bold italic backcolor link | \
             bullist numlist outdent indent | removeformat",
          paste_block_drop: true,
        }}
      />
    );
  }
}
