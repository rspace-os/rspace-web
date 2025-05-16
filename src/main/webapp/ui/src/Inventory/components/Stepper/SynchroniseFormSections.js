//@flow

import React, { type Node } from "react";
import FormSectionsContext from "../../../stores/contexts/FormSections";
import { mapObject } from "../../../util/Util";
import useUiPreference, { PREFERENCES } from "../../../util/useUiPreference";

const defaultFormSectionExpandedState = () =>
  Object.freeze({
    container: ({
      information: false,
      overview: true,
      details: false,
      barcodes: false,
      identifiers: false,
      attachments: false,
      permissions: false,
      customFields: false,
      locationsAndContent: true,
    }: {
      [string]: boolean,
    }),
    sample: ({
      information: false,
      overview: true,
      details: false,
      barcodes: false,
      identifiers: false,
      attachments: false,
      permissions: false,
      customFields: false,
      subsamples: true,
    }: {
      [string]: boolean,
    }),
    subSample: ({
      information: false,
      overview: true,
      details: true,
      barcodes: false,
      identifiers: false,
      attachments: false,
      permissions: false,
      customFields: false,
      notes: false,
    }: {
      [string]: boolean,
    }),
    sampleTemplate: ({
      overview: true,
      details: false,
      permissions: false,
      customFields: false,
      samples: true,
    }: {
      [string]: boolean,
    }),
    mixed: ({
      information: false,
      overview: true,
      details: false,
      barcodes: false,
    }: {
      [string]: boolean,
    }),
  });

type SynchroniseFormSectionsArgs = {|
  children: Node,
|};

export default function SynchroniseFormSections({
  children,
}: SynchroniseFormSectionsArgs): Node {
  const [formSectionExpandedState, setFormSectionExpandedState] =
    useUiPreference(PREFERENCES.INVENTORY_FORM_SECTIONS_EXPANDED, {
      defaultValue: defaultFormSectionExpandedState(),
    });
  return (
    <FormSectionsContext.Provider
      value={{
        isExpanded: (recordType, sectionName) =>
          formSectionExpandedState[recordType][sectionName],
        setExpanded: (recordType, sectionName, value) => {
          const formSectionExpandedStateCopy = { ...formSectionExpandedState };
          formSectionExpandedStateCopy[recordType][sectionName] = value;
          setFormSectionExpandedState(formSectionExpandedStateCopy);
        },
        setAllExpanded: (recordType, value) => {
          const formSectionExpandedStateCopy = { ...formSectionExpandedState };
          formSectionExpandedStateCopy[recordType] = mapObject(
            () => value,
            formSectionExpandedState[recordType]
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
}: SynchroniseFormSectionsArgs): Node {
  const [formSectionExpandedState, setFormSectionExpandedState] = React.useState(defaultFormSectionExpandedState());
  return (
    <FormSectionsContext.Provider value={{
      isExpanded: (recordType, sectionName) =>
        formSectionExpandedState[recordType][sectionName],
      setExpanded: (recordType, sectionName, value) => {
        const formSectionExpandedStateCopy = { ...formSectionExpandedState };
        formSectionExpandedStateCopy[recordType][sectionName] = value;
        setFormSectionExpandedState(formSectionExpandedStateCopy);
      },
      setAllExpanded: (recordType, value) => {
        const formSectionExpandedStateCopy = { ...formSectionExpandedState };
        formSectionExpandedStateCopy[recordType] = mapObject(
          () => value,
          formSectionExpandedState[recordType]
        );
        setFormSectionExpandedState(formSectionExpandedStateCopy);
      },
    }}>
      {children}
    </FormSectionsContext.Provider>
  );
}
