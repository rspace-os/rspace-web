//@flow

import React, { type Node } from "react";
import Toolbar from "../Toolbar/Toolbar";
import CommonEditActions from "../CommonEditActions";
import { type Editable } from "../../../stores/definitions/Editable";
import { type AllowedFormTypes } from "../../../stores/contexts/FormSections";
import { HeadingContext } from "../../../components/DynamicHeadingLevel";
import Box from "@mui/material/Box";

type FormWrapperArgs = {|
  titleText: string,
  recordType: AllowedFormTypes,
  children: Node,
  editableObject: Editable,
|};

/**
 * A wrapper for batch editing forms that provides the floating toolbar and the
 * footer with the save/cancel buttons.
 */
export default function FormWrapper({
  titleText,
  recordType,
  children,
  editableObject,
}: FormWrapperArgs): Node {
  return (
    <>
      <Toolbar title={titleText} recordType={recordType} batch />
      <Box sx={{ minHeight: 0, overflowY: "auto" }}>
        <HeadingContext level={3}>{children}</HeadingContext>
        <CommonEditActions editableObject={editableObject} />
      </Box>
    </>
  );
}
