//@flow

import React, { useState, useEffect, type Node } from "react";
import HtmlXmlExport, { type HtmlXmlExportDetailsArgs } from "./HtmlXmlExport";
import PdfExport, {
  type PdfExportDetailsArgs,
  type PdfExportDetails,
} from "./PdfExport";
import WordExport, {
  type WordExportDetailsArgs,
  type WordExportDetails,
} from "./WordExport";
import { type Validator } from "../util/Validator";

export type PageSize = "A4" | "LETTER";

type FormatSpecificArgs =
  | {|
      exportType: "pdf",
      ...PdfExportDetailsArgs,
    |}
  | {|
      exportType: "doc",
      ...WordExportDetailsArgs,
    |}
  | {|
      exportType: "html" | "xml",
      ...HtmlXmlExportDetailsArgs,
    |};

type FormatSpecificOptionsArgs = {|
  ...FormatSpecificArgs,
  validator: Validator,
|};

export default function FormatSpecificOptions(
  /*
   * `props` is not destructured inline, as it usually is, because Flow needs
   * us to keep the disjoint union object bundled together so that type
   * refinements on the `exportType` property of `props` can be used to infer
   * information about the types of the other props.
   */
  props: FormatSpecificOptionsArgs
): Node {
  const [inputValidations, setInputValidations] = useState({
    exportName: false,
  });
  const [submitAttempt, setSubmitAttempt] = useState(false);

  useEffect(() => {
    props.validator.setValidFunc(() =>
      Promise.resolve(
        (props.exportType !== "pdf" && props.exportType !== "doc") ||
          props.exportDetails.exportName.length > 2
      )
    );
  }, []);

  return (
    <>
      {props.exportType === "pdf" && (
        <PdfExport
          validations={{ inputValidations, submitAttempt }}
          exportDetails={props.exportDetails}
          updateExportDetails={<T: $Keys<PdfExportDetails>>(key: T, value) => {
            props.updateExportDetails(key, value);
            const newExportName =
              // For some reason, Flow needs this typeof check. Likely a bug
              key === "exportName" && typeof value === "string"
                ? value
                : props.exportDetails.exportName;
            setInputValidations({
              exportName: newExportName.length > 2,
            });
            setSubmitAttempt(true);
          }}
        />
      )}
      {props.exportType === "doc" && (
        <WordExport
          validations={{ inputValidations, submitAttempt }}
          exportDetails={props.exportDetails}
          updateExportDetails={<T: $Keys<WordExportDetails>>(key: T, value) => {
            props.updateExportDetails(key, value);
            const newExportName =
              // For some reason, Flow needs this typeof check. Likely a bug
              key === "exportName" && typeof value === "string"
                ? value
                : props.exportDetails.exportName;
            setInputValidations({
              exportName: newExportName.length > 2,
            });
            setSubmitAttempt(true);
          }}
        />
      )}
      {props.exportType !== "pdf" && props.exportType !== "doc" && (
        <HtmlXmlExport
          validations={{ inputValidations, submitAttempt }}
          exportDetails={props.exportDetails}
          updateExportDetails={(key, value) => {
            props.updateExportDetails(key, value);
            setInputValidations({ exportName: true });
            setSubmitAttempt(true);
          }}
        />
      )}
    </>
  );
}
