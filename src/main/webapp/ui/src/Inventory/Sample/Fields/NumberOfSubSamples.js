//@flow

import React, { type Node, useState, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import NumberField from "../../../components/Inputs/NumberField";
import SampleModel, {
  type SubSampleTargetLocation,
} from "../../../stores/models/SampleModel";
import FormField from "../../../components/Inputs/FormField";
import RadioField, {
  OptionHeading,
  OptionExplanation,
} from "../../../components/Inputs/RadioField";
import { styled } from "@mui/material/styles";
import { textFieldClasses } from "@mui/material/TextField";

const NestedFormField = styled(FormField)(({ theme }) => ({
  marginTop: "0 !important",
  marginLeft: `${theme.spacing(4)} !important`,
  [`& .${textFieldClasses.root}`]: {
    maxWidth: `min(100px, calc(100% - ${theme.spacing(4)}))`,
  },
}));

const MIN = 2;
const MAX = 100;

type NumberOfSubSamplesArgs = {|
  onErrorStateChange: (boolean) => void,
  sample: SampleModel,
|};

function NumberOfSubSamples({
  onErrorStateChange,
  sample,
}: NumberOfSubSamplesArgs): Node {
  /*
   * When tapping the "Create" context button for a container, the user has the
   * option to create a sample with respect to the container. The user can
   * select a single location and is then re-directed to the create-sample
   * form. Because only one location is selectable, it is important that the
   * create-sample form only allows the user to create a single subsample.
   */
  const withSpecifiedLocations: boolean =
    sample.newSampleSubSampleTargetLocations?.some(
      ({ location }: SubSampleTargetLocation) =>
        Object.keys(location).length > 0
    ) ?? false;
  const fixedNumberOfSubSamples: boolean =
    sample.beingCreatedInContainer && withSpecifiedLocations;

  const [valid, setValid] = useState(true);
  const [count, setCount] = useState(fixedNumberOfSubSamples ? "1" : "2");
  const [type, setType] = useState<"SINGULAR" | "MANY">("SINGULAR");

  const handleChange = ({
    target,
  }: {
    target: { value: string, checkValidity: () => boolean, ... },
    ...
  }) => {
    setCount(target.value);
    if (!target.checkValidity() || target.value === "") {
      setValid(false);
      sample.setAttributesDirty({
        newSampleSubSamplesCount: null,
      });
      onErrorStateChange(true);
    } else {
      setValid(true);
      sample.setAttributesDirty({
        newSampleSubSamplesCount: parseInt(target.value, 10),
      });
      onErrorStateChange(false);
    }
  };

  const errorMessage = valid
    ? ""
    : `Must be an integer value of at least ${MIN} and no more than ${MAX}.`;

  return (
    <>
      <FormField
        label="Type"
        value={type}
        doNotAttachIdToLabel
        asFieldset
        renderInput={({ id: _id, error: _error, ...props }) => (
          <RadioField
            name="type"
            onChange={({ target: { value } }) => {
              if (!value) return;
              setType(value);
              if (value === "MANY") {
                sample.setAttributesDirty({
                  newSampleSubSamplesCount: parseInt(count, 10),
                });
              } else {
                sample.setAttributesDirty({
                  newSampleSubSamplesCount: 1,
                });
              }
            }}
            options={[
              {
                value: "SINGULAR",
                label: (
                  <>
                    <OptionHeading>Individual Sample</OptionHeading>
                    <OptionExplanation>
                      The sample is made up of <strong>one subsample</strong>,
                      representing the physical location of the sample. Sample
                      actions are equivalent to subsample actions.
                    </OptionExplanation>
                  </>
                ),
              },
              {
                value: "MANY",
                label: (
                  <>
                    <OptionHeading>Sample with subsamples</OptionHeading>
                    <OptionExplanation>
                      The sample is made up of{" "}
                      <strong>multiple subsamples</strong>, which represent
                      related physical items originating from the same source.
                      Sample actions affect the entire group of subsamples.
                    </OptionExplanation>
                  </>
                ),
              },
            ]}
            {...props}
          />
        )}
      />
      {type === "MANY" && (
        <NestedFormField
          helperText={errorMessage}
          explanation={
            fixedNumberOfSubSamples
              ? "You can create and move additional subsamples into the container once the sample is created."
              : ""
          }
          label={`Number of ${sample.subSampleAlias.plural}`}
          error={!valid}
          value={count}
          disabled={fixedNumberOfSubSamples}
          renderInput={(props) => (
            <NumberField
              {...props}
              onChange={handleChange}
              inputProps={{
                min: MIN,
                max: MAX,
                step: 1,
              }}
            />
          )}
        />
      )}
    </>
  );
}

export default (observer(
  NumberOfSubSamples
): ComponentType<NumberOfSubSamplesArgs>);
