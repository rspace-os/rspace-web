import Box from "@mui/material/Box";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import ChoiceField from "../../../components/Inputs/ChoiceField";
import DateField from "../../../components/Inputs/DateField";
import NumberField from "../../../components/Inputs/NumberField";
import RadioField from "../../../components/Inputs/RadioField";
import ReferenceField from "../../../components/Inputs/ReferenceField";
import StringField from "../../../components/Inputs/StringField";
import TextField from "../../../components/Inputs/TextField";
import TimeField from "../../../components/Inputs/TimeField";
import UriField from "../../../components/Inputs/UriField";
import type { GalleryFile } from "../../../eln/gallery/useGalleryListing";
import type { Field } from "../../../stores/definitions/Field";
import { truncateIsoTimestamp } from "../../../stores/definitions/Units";
import type InstrumentModel from "../../../stores/models/InstrumentModel";
import AttachmentField from "../../components/Fields/Attachments/AttachmentField";
import FormField from "../../components/Inputs/FormField";

type TemplateFieldsArgs = {
  onErrorStateChange: (fieldName: string, hasError: boolean) => void;
  instrument: InstrumentModel;
};

function TemplateFields({ onErrorStateChange, instrument }: TemplateFieldsArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  return (instrument.fields ?? []).map((field: Field) => {
    const commonProps = {
      disabled: !instrument.isFieldEditable("fields"),
      label: field.name,
      error: (field.mandatory && !field.hasContent) || field.error,
      helperText: field.mandatory
        ? t("instrument.templateFields.mandatory")
        : field.error
          ? t("instrument.templateFields.invalid")
          : "",
      required: field.mandatory,
    };

    if (field.type === "attachment") {
      if (typeof field.content === "number") throw new Error("Invalid content type");
      const description = String(field.content);
      return (
        <FormField
          {...commonProps}
          key={field.name}
          value={{ description, attachment: field.attachment }}
          doNotAttachIdToLabel
          renderInput={({ id: _id, value, ...props }) => (
            <AttachmentField
              {...props}
              value={value.description}
              fieldOwner={instrument}
              onChange={({ target }) => {
                field.setAttributesDirty({ content: target.value });
                onErrorStateChange(`template_${field.name}`, (field.mandatory && !field.hasContent) || field.error);
              }}
              attachment={value.attachment}
              onAttachmentChange={(file: GalleryFile | File) => {
                field.setAttachment(file);
              }}
            />
          )}
        />
      );
    }

    if (field.type === "choice") {
      return (
        <FormField
          {...commonProps}
          key={field.name}
          value={field.selectedOptions ?? []}
          asFieldset
          doNotAttachIdToLabel
          renderInput={({ error: _error, id: _id, ...props }) => (
            <ChoiceField
              {...props}
              name={field.name}
              options={field.options.map(({ label, value }) => ({
                label,
                value,
                disabled: false,
                editing: false,
              }))}
              onChange={({ target }) => {
                field.setAttributesDirty({ selectedOptions: target.value });
                onErrorStateChange(`template_${field.name}`, (field.mandatory && !field.hasContent) || field.error);
              }}
            />
          )}
        />
      );
    }

    if (field.type === "date") {
      if (typeof field.content === "number") throw new Error("Invalid content type");
      const value = field.content as string | Date | null;
      return (
        <FormField
          {...commonProps}
          key={field.name}
          value={value}
          renderInput={({ error: _error, ...props }) => (
            <DateField
              {...props}
              onChange={({ target }) => {
                field.setAttributesDirty({
                  content: target.value ? truncateIsoTimestamp(target.value, "date").orElse("NaN-NaN-NaN") : null,
                });
                onErrorStateChange(`template_${field.name}`, (field.mandatory && !field.hasContent) || field.error);
              }}
            />
          )}
        />
      );
    }

    if (field.type === "number") {
      return (
        <FormField
          {...commonProps}
          key={field.name}
          value={field.content as string | number}
          renderInput={({ error: _error, ...props }) => (
            <NumberField
              {...props}
              onChange={({ target }) => {
                field.setAttributesDirty({ content: target.value });
                field.setError(!target.checkValidity());
                onErrorStateChange(`template_${field.name}`, (field.mandatory && !field.hasContent) || field.error);
              }}
              slotProps={{ htmlInput: { step: "any" } }}
            />
          )}
        />
      );
    }

    if (field.type === "radio") {
      return (
        <FormField
          {...commonProps}
          key={field.name}
          value={(field.selectedOptions ?? []).join("")}
          asFieldset
          doNotAttachIdToLabel
          renderInput={({ error: _error, id: _id, ...props }) => (
            <RadioField
              {...props}
              name={field.name}
              options={field.options.map(({ label, value }) => ({
                label,
                value,
                disabled: false,
                editing: false,
              }))}
              onChange={({ target }) => {
                field.setAttributesDirty({ selectedOptions: [target.value] });
                onErrorStateChange(`template_${field.name}`, (field.mandatory && !field.hasContent) || field.error);
              }}
            />
          )}
        />
      );
    }

    if (field.type === "reference") {
      return <FormField {...commonProps} key={field.name} value={void 0} renderInput={() => <ReferenceField />} />;
    }

    if (field.type === "string") {
      if (typeof field.content === "number") throw new Error("Invalid content type");
      return (
        <FormField
          {...commonProps}
          key={field.name}
          value={String(field.content)}
          renderInput={(props) => (
            <StringField
              {...props}
              onChange={({ target }) => {
                field.setAttributesDirty({ content: target.value });
                onErrorStateChange(`template_${field.name}`, (field.mandatory && !field.hasContent) || field.error);
              }}
            />
          )}
        />
      );
    }

    if (field.type === "text") {
      if (typeof field.content === "number") throw new Error("Invalid content type");
      return (
        <FormField
          {...commonProps}
          key={field.name}
          value={String(field.content)}
          doNotAttachIdToLabel
          renderInput={({ error: _error, id: _id, ...props }) => (
            <TextField
              {...props}
              onChange={({ target }) => {
                field.setAttributesDirty({ content: target.value });
                onErrorStateChange(`template_${field.name}`, (field.mandatory && !field.hasContent) || field.error);
              }}
            />
          )}
        />
      );
    }

    if (field.type === "time") {
      if (typeof field.content === "number") throw new Error("Invalid content type");
      const timeValue =
        field.content instanceof Date
          ? field.content.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", hour12: false })
          : String(field.content);
      return (
        <FormField
          {...commonProps}
          key={field.name}
          value={timeValue}
          renderInput={({ error: _error, ...props }) => (
            <TimeField
              {...props}
              onChange={({ target }) => {
                field.setAttributesDirty({ content: target.value });
                field.setError(target.value ? Number.isNaN(Number(target.value)) : false);
                onErrorStateChange(`template_${field.name}`, (field.mandatory && !field.hasContent) || field.error);
              }}
            />
          )}
        />
      );
    }

    if (field.type === "uri") {
      if (typeof field.content === "number") throw new Error("Invalid content type");
      return (
        <FormField
          {...commonProps}
          key={field.name}
          value={String(field.content)}
          renderInput={({ error: _error, ...props }) => (
            <UriField
              {...props}
              onChange={({ target }) => {
                field.setAttributesDirty({ content: target.value });
                field.setError(false);
                onErrorStateChange(`template_${field.name}`, (field.mandatory && !field.hasContent) || field.error);
              }}
            />
          )}
        />
      );
    }

    return <Box key={field.name}>{t("instrument.templateFields.unknownFieldType", { type: field.type })}</Box>;
  });
}

export default observer(TemplateFields);
