import { describe, expect, test } from "vitest";
import "@/__tests__/__mocks__/useUiPreference";
import { render } from "@testing-library/react";
import { useContext, useEffect } from "react";
import FormSectionsContext from "../../../../stores/contexts/FormSections";
import type { RecordType } from "../../../../stores/definitions/InventoryRecord";
import SynchroniseFormSections from "../SynchroniseFormSections";

function MockFormSectionInOuterContext() {
  const formSectionContext = useContext(FormSectionsContext);
  if (!formSectionContext) throw new Error("FormSectionContext is required by StepperPanel");

  const { setExpanded } = formSectionContext;
  useEffect(() => {
    // this closes the form section as managed by the outer context...
    setExpanded("container", "overview", false);
  }, []);
  // biome-ignore lint/complexity/noUselessFragments: initial biome migration
  return <></>;
}
function MockFormSectionInInnerContext({
  onMountExpectFn,
}: {
  onMountExpectFn: (f: (recordType: RecordType, section: string) => boolean) => void;
}) {
  const formSectionContext = useContext(FormSectionsContext);
  if (!formSectionContext) throw new Error("FormSectionContext is required by StepperPanel");
  useEffect(() => {
    onMountExpectFn((recordType, section) => formSectionContext.isExpanded(recordType, section));
  }, []);
  // biome-ignore lint/complexity/noUselessFragments: initial biome migration
  return <></>;
}
describe("SynchroniseFormSections", () => {
  test("Nesting SynchroniseFormSections should result in the inner one taking effect.", () => {
    const onMountExpectFn = (isExpanded: (recordType: RecordType, section: string) => boolean) => {
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
      </SynchroniseFormSections>,
    );
  });
});
