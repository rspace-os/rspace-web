/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React, { useEffect, useContext } from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import SynchroniseFormSections from "../SynchroniseFormSections";
import FormSectionsContext from "../../../../stores/contexts/FormSections";
import { type RecordType } from "../../../../stores/definitions/InventoryRecord";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

function MockFormSectionInOuterContext(_: {||}) {
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
  onMountExpectFn: ((RecordType, string) => boolean) => void,
}) {
  const formSectionContext = useContext(FormSectionsContext);
  if (!formSectionContext)
    throw new Error("FormSectionContext is required by StepperPanel");
  const { isExpanded } = formSectionContext;

  useEffect(() => {
    onMountExpectFn(isExpanded);
  }, []);

  return <></>;
}

describe("SynchroniseFormSections", () => {
  test("Nesting SynchroniseFormSections should result in the inner one taking effect.", () => {
    const onMountExpectFn = (isExpanded: (RecordType, string) => boolean) => {
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
