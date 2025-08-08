import React from "react";
import FormSectionsContext from "../../../stores/contexts/FormSections";
import useUiPreference, {
  PREFERENCES,
} from "../../../hooks/api/useUiPreference";

type ContainerSections = {
  information: boolean;
  overview: boolean;
  details: boolean;
  barcodes: boolean;
  identifiers: boolean;
  attachments: boolean;
  permissions: boolean;
  customFields: boolean;
  locationsAndContent: boolean;
};

type SampleSections = {
  information: boolean;
  overview: boolean;
  details: boolean;
  barcodes: boolean;
  identifiers: boolean;
  attachments: boolean;
  permissions: boolean;
  customFields: boolean;
  subsamples: boolean;
};

type SubSampleSections = {
  information: boolean;
  overview: boolean;
  details: boolean;
  barcodes: boolean;
  identifiers: boolean;
  attachments: boolean;
  permissions: boolean;
  customFields: boolean;
  notes: boolean;
};

type SampleTemplateSections = {
  overview: boolean;
  details: boolean;
  permissions: boolean;
  customFields: boolean;
  samples: boolean;
};

type MixedSections = {
  information: boolean;
  overview: boolean;
  details: boolean;
  barcodes: boolean;
};

type FormSectionsState = {
  container: ContainerSections;
  sample: SampleSections;
  subSample: SubSampleSections;
  sampleTemplate: SampleTemplateSections;
  mixed: MixedSections;
};

type RecordType = keyof FormSectionsState;
type SectionName<T extends RecordType> = keyof FormSectionsState[T];

const defaultFormSectionExpandedState = (): FormSectionsState =>
  Object.freeze({
    container: {
      information: false,
      overview: true,
      details: false,
      barcodes: false,
      identifiers: false,
      attachments: false,
      permissions: false,
      customFields: false,
      locationsAndContent: true,
    },
    sample: {
      information: false,
      overview: true,
      details: false,
      barcodes: false,
      identifiers: false,
      attachments: false,
      permissions: false,
      customFields: false,
      subsamples: true,
    },
    subSample: {
      information: false,
      overview: true,
      details: true,
      barcodes: false,
      identifiers: false,
      attachments: false,
      permissions: false,
      customFields: false,
      notes: false,
    },
    sampleTemplate: {
      overview: true,
      details: false,
      permissions: false,
      customFields: false,
      samples: true,
    },
    mixed: {
      information: false,
      overview: true,
      details: false,
      barcodes: false,
    },
  });

type SynchroniseFormSectionsArgs = {
  children: React.ReactNode;
};

// Helper function to transform all section values
function setAllSectionValues<T extends RecordType>(
  recordType: T,
  value: boolean,
  state: FormSectionsState,
): FormSectionsState[T] {
  const result = {} as FormSectionsState[T];
  const section = state[recordType];

  Object.keys(section).forEach((key) => {
    (result as Record<string, boolean>)[key] = value;
  });
  return result;
}

export default function SynchroniseFormSections({
  children,
}: SynchroniseFormSectionsArgs): React.ReactNode {
  const [formSectionExpandedState, setFormSectionExpandedState] =
    useUiPreference(PREFERENCES.INVENTORY_FORM_SECTIONS_EXPANDED, {
      defaultValue: defaultFormSectionExpandedState(),
    });
  return (
    <FormSectionsContext.Provider
      value={{
        isExpanded: <T extends RecordType>(
          recordType: T,
          sectionName: SectionName<T>,
        ) => {
          return formSectionExpandedState[recordType][sectionName];
        },
        setExpanded: <T extends RecordType>(
          recordType: T,
          sectionName: SectionName<T>,
          value: boolean,
        ) => {
          const formSectionExpandedStateCopy = { ...formSectionExpandedState };
          (formSectionExpandedStateCopy[recordType] as Record<string, boolean>)[
            sectionName as string
          ] = value;
          setFormSectionExpandedState(formSectionExpandedStateCopy);
        },
        setAllExpanded: <T extends RecordType>(
          recordType: T,
          value: boolean,
        ) => {
          const formSectionExpandedStateCopy = { ...formSectionExpandedState };
          formSectionExpandedStateCopy[recordType] = setAllSectionValues(
            recordType,
            value,
            formSectionExpandedState,
          );
          setFormSectionExpandedState(formSectionExpandedStateCopy);
        },
      }}
    >
      {children}
    </FormSectionsContext.Provider>
  );
}

/**
 * Sometimes we specifically don't want to synchronise form sections, and the
 * default open/close state should always be how the section is initially
 * presented. For example, when creating new records we want to always present
 * the required name field in the overview section. The user can always toggle
 * the state of the section, but that state change is not persisted.
 */
export function UnsynchroniseFormSections({
  children,
}: SynchroniseFormSectionsArgs): React.ReactNode {
  const [formSectionExpandedState, setFormSectionExpandedState] =
    React.useState(defaultFormSectionExpandedState());
  return (
    <FormSectionsContext.Provider
      value={{
        isExpanded: <T extends RecordType>(
          recordType: T,
          sectionName: SectionName<T>,
        ) => {
          return formSectionExpandedState[recordType][sectionName];
        },
        setExpanded: <T extends RecordType>(
          recordType: T,
          sectionName: SectionName<T>,
          value: boolean,
        ) => {
          const formSectionExpandedStateCopy = { ...formSectionExpandedState };
          (formSectionExpandedStateCopy[recordType] as Record<string, boolean>)[
            sectionName as string
          ] = value;
          setFormSectionExpandedState(formSectionExpandedStateCopy);
        },
        setAllExpanded: <T extends RecordType>(
          recordType: T,
          value: boolean,
        ) => {
          const formSectionExpandedStateCopy = { ...formSectionExpandedState };
          formSectionExpandedStateCopy[recordType] = setAllSectionValues(
            recordType,
            value,
            formSectionExpandedState,
          );
          setFormSectionExpandedState(formSectionExpandedStateCopy);
        },
      }}
    >
      {children}
    </FormSectionsContext.Provider>
  );
}
