import Box from "@mui/material/Box";
import { textFieldClasses } from "@mui/material/TextField";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import NumberField from "../../../components/Inputs/NumberField";
import RadioField, { OptionExplanation, OptionHeading } from "../../../components/Inputs/RadioField";
import type SampleModel from "../../../stores/models/SampleModel";
import type { SubSampleTargetLocation } from "../../../stores/models/SampleModel";
import FormField from "../../components/Inputs/FormField";

const MIN = 2;
const MAX = 100;

type NumberOfSubSamplesArgs = {
  onErrorStateChange: (value: boolean) => void;
  sample: SampleModel;
};

function NumberOfSubSamples({ onErrorStateChange, sample }: NumberOfSubSamplesArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  /*
   * When tapping the "Create" context button for a container, the user has the
   * option to create a sample with respect to the container. The user can
   * select a single location and is then re-directed to the create-sample
   * form. Because only one location is selectable, it is important that the
   * create-sample form only allows the user to create a single subsample.
   */
  const withSpecifiedLocations: boolean =
    sample.newSampleSubSampleTargetLocations?.some(
      ({ location }: SubSampleTargetLocation) => Object.keys(location).length > 0,
    ) ?? false;
  const fixedNumberOfSubSamples: boolean = sample.beingCreatedInContainer && withSpecifiedLocations;

  const [valid, setValid] = useState(true);
  const [count, setCount] = useState(fixedNumberOfSubSamples ? "1" : "2");
  const [type, setType] = useState<"SINGULAR" | "MANY">("SINGULAR");

  const handleChange = ({ target }: { target: { value: string; checkValidity: () => boolean } }) => {
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

  const errorMessage = valid ? "" : t("sample.fields.numberOfSubsamples.errorMessage", { min: MIN, max: MAX });

  return (
    <>
      <FormField
        label={t("sample.fields.numberOfSubsamples.type")}
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
                    <OptionHeading>{t("sample.fields.numberOfSubsamples.singular.heading")}</OptionHeading>
                    <OptionExplanation>
                      <TransRichText ns="inventory" i18nKey="sample.fields.numberOfSubsamples.singular.explanation" />
                    </OptionExplanation>
                  </>
                ),
              },
              {
                value: "MANY",
                label: (
                  <>
                    <OptionHeading>{t("sample.fields.numberOfSubsamples.many.heading")}</OptionHeading>
                    <OptionExplanation>
                      <TransRichText ns="inventory" i18nKey="sample.fields.numberOfSubsamples.many.explanation" />
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
        <Box
          sx={(theme) => ({
            marginTop: "0 !important",
            marginLeft: `${theme.spacing(4)} !important`,
            [`& .${textFieldClasses.root}`]: {
              maxWidth: `min(100px, calc(100% - ${theme.spacing(4)}))`,
            },
          })}
        >
          <FormField
            helperText={errorMessage}
            explanation={fixedNumberOfSubSamples ? t("sample.fields.numberOfSubsamples.fixedExplanation") : ""}
            label={t("sample.fields.numberOfSubsamples.count", { plural: sample.subSampleAlias.plural })}
            error={!valid}
            value={count}
            disabled={fixedNumberOfSubSamples}
            renderInput={(props) => (
              <NumberField
                {...props}
                onChange={handleChange}
                slotProps={{
                  htmlInput: {
                    min: MIN,
                    max: MAX,
                    step: 1,
                  },
                }}
              />
            )}
          />
        </Box>
      )}
    </>
  );
}

export default observer(NumberOfSubSamples);
