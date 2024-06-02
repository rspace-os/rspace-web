//@flow

import React, { type Node } from "react";
import Toolbar from "../Toolbar/Toolbar";
import CommonEditActions from "../CommonEditActions";
import { type Editable } from "../../../stores/definitions/Editable";
import { type AllowedFormTypes } from "../../../stores/contexts/FormSections";
import { HeadingContext } from "../../../components/DynamicHeadingLevel";

type FormWrapperArgs = {|
  titleText: string,
  recordType: AllowedFormTypes,
  children: Node,
  editableObject: Editable,
|};

export default function FormWrapper({
  titleText,
  recordType,
  children,
  editableObject,
}: FormWrapperArgs): Node {
  return (
    <>
      <Toolbar title={titleText} recordType={recordType} batch />
      <HeadingContext level={3}>{children}</HeadingContext>
      <CommonEditActions editableObject={editableObject} />
    </>
  );
}
