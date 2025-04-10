import React, { useState, useEffect } from "react";
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

type FormatSpecificArgs =
  | ({
      exportType: "pdf";
    } & PdfExportDetailsArgs)
  | ({
      exportType: "doc";
    } & WordExportDetailsArgs)
  | ({
      exportType: "html" | "xml" | "eln";
    } & HtmlXmlExportDetailsArgs);

type FormatSpecificOptionsArgs = FormatSpecificArgs & {
  validator: Validator;
};

export default function FormatSpecificOptions(
  /*
   * `props` is not destructured inline, as it usually is, because Flow needs
   * us to keep the disjoint union object bundled together so that type
   * refinements on the `exportType` property of `props` can be used to infer
   * information about the types of the other props.
   */
  props: FormatSpecificOptionsArgs
): React.ReactNode {
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
          updateExportDetails={<T extends keyof PdfExportDetails>(
            key: T,
            value: PdfExportDetails[T]
          ) => {
            props.updateExportDetails(key, value);
            const newExportName =
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
          updateExportDetails={<T extends keyof WordExportDetails>(
            key: T,
            value: WordExportDetails[T]
          ) => {
            props.updateExportDetails(key, value);
            const newExportName =
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
