//@flow

import React, { type Node, useState, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import NumberField from "../../../components/Inputs/NumberField";
import SampleModel, {
  type SubSampleTargetLocation,
} from "../../../stores/models/SampleModel";
import FormField from "../../../components/Inputs/FormField";

const MIN = 1;
const MAX = 100;

type NumberOfSubSamplesArgs = {|
  onErrorStateChange: (boolean) => void,
  sample: SampleModel,
|};

function NumberOfSubSamples({
  onErrorStateChange,
  sample,
}: NumberOfSubSamplesArgs): Node {
  const [valid, setValid] = useState(true);
  const [count, setCount] = useState("1");

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
    : `Should be a positive integer smaller than or equal to ${MAX}.`;

  const withSpecifiedLocations: boolean = // inImaGridContainer
    sample.newSampleSubSampleTargetLocations?.some(
      ({ location }: SubSampleTargetLocation) =>
        Object.keys(location).length > 0
    ) ?? false;
  const fixedNumberOfSubSamples: boolean =
    sample.beingCreatedInContainer && withSpecifiedLocations;

  return (
    <FormField
      helperText={errorMessage}
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
  );
}

export default (observer(
  NumberOfSubSamples
): ComponentType<NumberOfSubSamplesArgs>);
