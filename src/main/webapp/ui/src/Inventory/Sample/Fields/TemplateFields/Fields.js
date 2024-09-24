//@flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import { type Sample } from "../../../../stores/definitions/Sample";
import Box from "@mui/material/Box";
import FormField from "../../../components/Inputs/FormField";
import AttachmentField from "../../../../components/Inputs/AttachmentField";
import ChoiceField from "../../../../components/Inputs/ChoiceField";
import DateField from "../../../../components/Inputs/DateField";
import { truncateIsoTimestamp } from "../../../../util/conversions";
import NumberField from "../../../../components/Inputs/NumberField";
import RadioField from "../../../../components/Inputs/RadioField";
import ReferenceField from "../../../../components/Inputs/ReferenceField";
import StringField from "../../../../components/Inputs/StringField";
import TextField from "../../../../components/Inputs/TextField";
import TimeField from "../../../../components/Inputs/TimeField";
import UriField from "../../../../components/Inputs/UriField";
import { type Field } from "../../../../stores/definitions/Field";
import Result from "../../../../stores/models/Result";
import { type GalleryFile } from "../../../../eln/gallery/useGalleryListing";

type FieldsArgs = {|
  onErrorStateChange: (string, boolean) => void,
  // The Sample type is to get the `fields`, the Result type is for AttachmentField's `fieldOwner` prop
  sample: Sample & Result,
|};

function Fields({ onErrorStateChange, sample }: FieldsArgs): Node {
  return (sample.fields ?? []).map((field: Field) => {
    const commonProps = {
      disabled: !sample.isFieldEditable("fields"),
      label: field.name,
      error: (field.mandatory && !field.hasContent) || field.error,
      helperText: field.mandatory
        ? "This field is mandatory. Please enter a value."
        : field.error
        ? "Invalid value. Please enter a valid value."
        : "",
      required: field.mandatory,
    };

    if (field.type === "attachment") {
      if (typeof field.content === "number")
        throw new Error("Invalid content type");
      const description: string = field.content;
      return (
        <FormField
          {...commonProps}
          key={field.name}
          /*
           * We pass both description and attachment because renderInput
           * is not a mobx observer so changes to properties on field (i.e.
           * doing something like `field.<something`) will not trigger a
           * re-rendering. As such, uploading a new file would not result
           * in it being shown in the UI until something else triggers a
           * re-rendering. By passing these via the `value` prop, all of the
           * fields will re-render when a new file is uploaded.
           */
          value={{ description, attachment: field.attachment }}
          // ID is not used because there is no singluar HTMLInputElement to attach it to
          doNotAttachIdToLabel
          renderInput={({ id: _id, value, ...props }) => (
            <AttachmentField
              {...props}
              value={value.description}
              fieldOwner={sample}
              onChange={({ target }) => {
                field.setAttributesDirty({ content: target.value });
                onErrorStateChange(
                  `template_${field.name}`,
                  (field.mandatory && !field.hasContent) || field.error
                );
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
          // ID is not used because there is no singluar HTMLInputElement to attach it to
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
                onErrorStateChange(
                  `template_${field.name}`,
                  (field.mandatory && !field.hasContent) || field.error
                );
              }}
            />
          )}
        />
      );
    }

    if (field.type === "date") {
      if (typeof field.content === "number")
        throw new Error("Invalid content type");
      const value = field.content;
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
                  content: target.value
                    ? truncateIsoTimestamp(target.value, "date").orElse(
                        "NaN-NaN-NaN"
                      )
                    : null,
                });
                onErrorStateChange(
                  `template_${field.name}`,
                  (field.mandatory && !field.hasContent) || field.error
                );
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
          value={field.content}
          renderInput={(props) => (
            <NumberField
              {...props}
              onChange={({ target }) => {
                field.setAttributesDirty({
                  content: target.value,
                });
                field.setError(!target.checkValidity());
                onErrorStateChange(
                  `template_${field.name}`,
                  (field.mandatory && !field.hasContent) || field.error
                );
              }}
              inputProps={{
                step: "any",
              }}
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
          // ID is not used because there is no singluar HTMLInputElement to attach it to
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
                onErrorStateChange(
                  `template_${field.name}`,
                  (field.mandatory && !field.hasContent) || field.error
                );
              }}
            />
          )}
        />
      );
    }

    if (field.type === "reference") {
      return (
        <FormField
          {...commonProps}
          key={field.name}
          value={void 0}
          renderInput={() => <ReferenceField />}
        />
      );
    }

    if (field.type === "string") {
      if (typeof field.content === "number")
        throw new Error("Invalid content type");
      return (
        <FormField
          {...commonProps}
          key={field.name}
          value={field.content}
          renderInput={(props) => (
            <StringField
              {...props}
              onChange={({ target }) => {
                field.setAttributesDirty({
                  content: target.value,
                });
                onErrorStateChange(
                  `template_${field.name}`,
                  (field.mandatory && !field.hasContent) || field.error
                );
              }}
            />
          )}
        />
      );
    }

    if (field.type === "text") {
      if (typeof field.content === "number")
        throw new Error("Invalid content type");
      return (
        <FormField
          {...commonProps}
          key={field.name}
          value={field.content}
          // ID is not used because TinyMCE does not expose an HTMLInputElement to attach it to
          doNotAttachIdToLabel
          renderInput={({ error: _error, id: _id, ...props }) => (
            <TextField
              {...props}
              onChange={({ target }) => {
                field.setAttributesDirty({
                  content: target.value,
                });
                onErrorStateChange(
                  `template_${field.name}`,
                  (field.mandatory && !field.hasContent) || field.error
                );
              }}
            />
          )}
        />
      );
    }

    if (field.type === "time") {
      if (typeof field.content === "number")
        throw new Error("Invalid content type");
      return (
        <FormField
          {...commonProps}
          key={field.name}
          value={field.content}
          renderInput={({ error: _error, ...props }) => (
            <TimeField
              {...props}
              onChange={({ target }) => {
                field.setAttributesDirty({
                  content: target.value,
                });
                field.setError(isNaN(target.value));
                onErrorStateChange(
                  `template_${field.name}`,
                  (field.mandatory && !field.hasContent) || field.error
                );
              }}
            />
          )}
        />
      );
    }

    if (field.type === "uri") {
      if (typeof field.content === "number")
        throw new Error("Invalid content type");
      return (
        <FormField
          {...commonProps}
          key={field.name}
          value={field.content}
          renderInput={({ error: _error, ...props }) => (
            <UriField
              {...props}
              onChange={({ target }) => {
                field.setAttributesDirty({
                  content: target.value,
                });
                field.setError(isNaN(target.value));
                onErrorStateChange(
                  `template_${field.name}`,
                  (field.mandatory && !field.hasContent) || field.error
                );
              }}
            />
          )}
        />
      );
    }

    return <Box key={field.name}>Unknown field type: {field.type}</Box>;
  });
}

export default (observer(Fields): ComponentType<FieldsArgs>);
