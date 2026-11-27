import { useStore } from "@tanstack/react-form";
import type React from "react";
import { useState } from "react";
import * as v from "valibot";
import type { ExportForm } from "./exportForm";
import HtmlXmlExport from "./HtmlXmlExport";
import PdfExport, { type PdfExportDetails } from "./PdfExport";
import WordExport, { type WordExportDetails } from "./WordExport";

const exportNameSchema = v.pipe(v.string(), v.minLength(3));

/**
 * The options of the chosen format. Only PDF and Word exports have a name,
 * which must be at least three characters long.
 */
export const exportDetailsSchema = v.nullable(v.looseObject({ exportName: v.optional(exportNameSchema) }));

type FormatSpecificOptionsArgs = {
  form: ExportForm;
};

function FormatSpecificOptions({ form }: FormatSpecificOptionsArgs): React.ReactNode {
  const exportDetails = useStore(form.store, (state) => state.values.exportDetails);
  // The name is only marked as invalid once the user has changed one of the options.
  const [submitAttempt, setSubmitAttempt] = useState(false);

  if (exportDetails === null) return null;

  if ("exportFormat" in exportDetails) {
    if (exportDetails.exportFormat === "PDF")
      return (
        <PdfExport
          validations={{
            inputValidations: { exportName: v.is(exportNameSchema, exportDetails.exportName) },
            submitAttempt,
          }}
          exportDetails={exportDetails}
          updateExportDetails={<T extends keyof PdfExportDetails>(key: T, value: PdfExportDetails[T]) => {
            form.setFieldValue("exportDetails", { ...exportDetails, [key]: value });
            setSubmitAttempt(true);
          }}
        />
      );
    return (
      <WordExport
        validations={{
          inputValidations: { exportName: v.is(exportNameSchema, exportDetails.exportName) },
          submitAttempt,
        }}
        exportDetails={exportDetails}
        updateExportDetails={<T extends keyof WordExportDetails>(key: T, value: WordExportDetails[T]) => {
          form.setFieldValue("exportDetails", { ...exportDetails, [key]: value });
          setSubmitAttempt(true);
        }}
      />
    );
  }

  return (
    <HtmlXmlExport
      validations={{ inputValidations: { exportName: true }, submitAttempt }}
      exportDetails={exportDetails}
      updateExportDetails={(key, value) => {
        form.setFieldValue("exportDetails", { ...exportDetails, [key]: value });
        setSubmitAttempt(true);
      }}
    />
  );
}

export default FormatSpecificOptions;
