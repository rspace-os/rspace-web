import DOMPurify from "dompurify";
import type React from "react";
import { useTranslation } from "react-i18next";
import NoValue from "../../components/NoValue";
import Editor from "./StyledTinyMceEditor";

export type TextFieldArgs = {
  value: string;
  disabled?: boolean;
  name?: string;
  noValueLabel?: string | null;
  variant?: "filled" | "outlined" | "standard";
  id?: string;

  /**
   * This fires both when the user type in the field and when the `value` prop changes.
   */
  onChange?: (event: { target: { name: string; value: string } }) => void;
};

export default function TextField({ onChange, ...props }: TextFieldArgs): React.ReactNode {
  const { t } = useTranslation("common");
  const handleEditorChange = (content: string) => {
    const e = {
      target: {
        name: props.name ?? "",
        value: content,
      },
    };
    if (onChange) onChange(e);
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
    return <NoValue label={props.noValueLabel ?? t("values.none")} />;
  }
  return (
    <Editor
      {...props}
      onEditorChange={handleEditorChange}
      init={{
        menubar: false,
        plugins: ["autolink", "lists", "link", "charmap", "autoresize"],
        toolbar:
          "undo redo blocks bold italic backcolor link \
             bullist numlist removeformat",
        paste_block_drop: true,
      }}
    />
  );
}
