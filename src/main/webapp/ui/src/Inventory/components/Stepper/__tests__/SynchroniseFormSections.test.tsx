/*
 */
import {
  describe,
  expect,
  beforeEach,
  it,
  vi,
} from "vitest";
import "@/__tests__/mocks/useUiPreference";
import React,
  { useEffect,
  useContext } from "react";
import {
  render,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import SynchroniseFormSections from "../SynchroniseFormSections";
import FormSectionsContext from "../../../../stores/contexts/FormSections";
import { type RecordType } from "../../../../stores/definitions/InventoryRecord";

beforeEach(() => {
  vi.clearAllMocks();
});


function MockFormSectionInOuterContext() {
  const formSectionContext = useContext(FormSectionsContext);
  if (!formSectionContext)
    throw new Error("FormSectionContext is required by StepperPanel");
  const { setExpanded } = formSectionContext;

  useEffect(() => {
    // this closes the form section as managed by the outer context...
    setExpanded("container", "overview", false);
  }, []);

  return <></>;
}

function MockFormSectionInInnerContext({
  onMountExpectFn,
}: {
  onMountExpectFn: (
    f: (recordType: RecordType, section: string) => boolean
  ) => void;
}) {
  const formSectionContext = useContext(FormSectionsContext);
  if (!formSectionContext)
    throw new Error("FormSectionContext is required by StepperPanel");

  useEffect(() => {
    onMountExpectFn((recordType, section) =>
      formSectionContext.isExpanded(recordType, section)
    );
  }, []);

  return <></>;
}

describe("SynchroniseFormSections", () => {
  it("Nesting SynchroniseFormSections should result in the inner one taking effect.", () => {
    const onMountExpectFn = (
      isExpanded: (recordType: RecordType, section: string) => boolean
    ) => {
      /*
       * ...but the inner context should still be saying that the form
       * section is open
       */
      expect(isExpanded("container", "overview")).toBe(true);
    };

    render(
      <SynchroniseFormSections>
        <MockFormSectionInOuterContext />
        <SynchroniseFormSections>
          <MockFormSectionInInnerContext onMountExpectFn={onMountExpectFn} />
        </SynchroniseFormSections>
      </SynchroniseFormSections>
    );
  });
});

