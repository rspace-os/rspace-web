/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import {
  render,
  cleanup,
  screen,
  within,
  fireEvent,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import CreateInContextDialog from "../CreateDialog";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
import { makeMockContainer } from "../../../stores/models/__tests__/ContainerModel/mocking";
import { makeMockSample } from "../../../stores/models/__tests__/SampleModel/mocking";
import { makeMockTemplate } from "../../../stores/models/__tests__/TemplateModel/mocking";
import {
  makeMockSubSample,
  subsampleAttrs,
} from "../../../stores/models/__tests__/SubSampleModel/mocking";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import getRootStore from "../../../stores/stores/RootStore";
import SearchContext from "../../../stores/contexts/Search";
import Search from "../../../stores/models/Search";
import { mockFactory } from "../../../stores/definitions/__tests__/Factory/mocking";
import each from "jest-each";
import userEvent from "@testing-library/user-event";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

const mockContainer = makeMockContainer();
const mockSample = makeMockSample();
const mockSubSample = makeMockSubSample();
const mockTemplate = makeMockTemplate();

const search = new Search({
  factory: mockFactory(),
});

const modalRoot = document.createElement("div");
modalRoot.setAttribute("id", "modal-root");

const Dialog = ({
  selectedResult,
  onClose,
}: {
  selectedResult: InventoryRecord,
  onClose: () => void,
}) => {
  return (
    <ThemeProvider theme={materialTheme}>
      <SearchContext.Provider
        value={{
          search,
          differentSearchForSettingActiveResult: search,
        }}
      >
        <CreateInContextDialog
          selectedResult={selectedResult}
          open={true}
          onClose={onClose}
          menuID=""
        />
      </SearchContext.Provider>
    </ThemeProvider>
  );
};

describe("CreateInContextDialog", () => {
  describe("Splitting", () => {
    each(["sample", "container", "subsample", "template"]).test(
      "Is the split numerical field shown when the record type is a %s",
      (type) => {
        const record = {
          sample: makeMockSample({
            subSamples: [subsampleAttrs({})],
          }),
          container: makeMockContainer({}),
          subsample: makeMockSubSample({}),
          template: makeMockTemplate({}),
        };
        render(<Dialog selectedResult={record[type]} onClose={() => {}} />);
        if (type === "sample" || type === "subsample") {
          expect(screen.getByRole("spinbutton")).toBeInTheDocument();
        } else {
          expect(screen.queryByRole("spinbutton")).not.toBeInTheDocument();
        }
      }
    );
  });

  describe("When Container selected", () => {
    test("Creation text options are rendered", () => {
      render(<Dialog selectedResult={mockContainer} onClose={() => {}} />);

      expect(screen.getByTestId("option-explanation-ic-1")).toHaveTextContent(
        "A new Container will be created in the selected Container."
      );
      expect(screen.getByTestId("option-explanation-ic-2")).toHaveTextContent(
        "A new Sample will be created and its Subsample(s) will be placed in the selected Container."
      );
    });

    test("Container creation option is disabled depending on canStore", () => {
      const canStoreS = makeMockContainer({ canStoreContainers: false });
      render(<Dialog selectedResult={canStoreS} onClose={() => {}} />);

      const containerCreationRadio = screen.getByTestId("option-radio-ic-1");
      expect(containerCreationRadio).toBeInTheDocument();
      expect(within(containerCreationRadio).getByRole("radio")).toBeDisabled();
    });

    test("Sample creation option is disabled depending on canStore", () => {
      const canStoreC = makeMockContainer({ canStoreSamples: false });
      render(<Dialog selectedResult={canStoreC} onClose={() => {}} />);

      const sampleCreationRadio = screen.getByTestId("option-radio-ic-2");
      expect(sampleCreationRadio).toBeInTheDocument();
      expect(within(sampleCreationRadio).getByRole("radio")).toBeDisabled();
    });
  });

  describe("When Sample selected", () => {
    test("Template creation option text is rendered", () => {
      render(<Dialog selectedResult={mockSample} onClose={() => {}} />);

      expect(screen.getByTestId("option-explanation-sa-1")).toHaveTextContent(
        "A new Template will be created from the selected Sample."
      );
    });
  });

  describe("When Template selected", () => {
    test("Sample creation option text is rendered", () => {
      render(<Dialog selectedResult={mockTemplate} onClose={() => {}} />);

      expect(screen.getByTestId("option-explanation-it-1")).toHaveTextContent(
        "A new Sample will be created from the selected Template."
      );
    });
  });

  describe("When Subsample selected", () => {
    test("Split option text is rendered", () => {
      render(<Dialog selectedResult={mockSubSample} onClose={() => {}} />);

      expect(screen.getByTestId("option-explanation-ss-1")).toHaveTextContent(
        "The selected Subsample will be split into parts."
      );
    });
  });

  describe("When CreateDialog is rendered", () => {
    test("Cancel button exists and can be clicked", async () => {
      const user = userEvent.setup();
      const onClose = jest.fn<[], void>();
      render(<Dialog selectedResult={mockContainer} onClose={onClose} />);

      await user.click(
        screen.getByRole("button", {
          name: /CANCEL/i,
        })
      );
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    test("Create button calls the right action/method (createNewContainer case)", async () => {
      const user = userEvent.setup();
      render(<Dialog selectedResult={mockContainer} onClose={() => {}} />);

      // with selectedResult={mockContainer}, createNewContainer will be the default option
      const createContainerSpy = jest.spyOn(
        getRootStore().searchStore,
        "createNewContainer"
      );

      await user.click(
        screen.getByRole("button", {
          name: /CREATE/i,
        })
      );
      expect(createContainerSpy).toHaveBeenCalledTimes(1);
    });

    test("Create button calls the right action/method (createNewSample case)", async () => {
      const user = userEvent.setup();
      render(<Dialog selectedResult={mockContainer} onClose={() => {}} />);

      // with selectedResult={mockContainer}, createNewSample will be the second option
      const createSampleSpy = jest.spyOn(
        getRootStore().searchStore,
        "createNewSample"
      );

      fireEvent.click(screen.getByTestId("option-radio-ic-2"));

      await user.click(
        screen.getByRole("button", {
          name: /CREATE/i,
        })
      );
      expect(createSampleSpy).toHaveBeenCalledTimes(1);
    });

    test("Create button calls the right action/method (setTemplateCreationContext case)", async () => {
      const user = userEvent.setup();
      render(<Dialog selectedResult={mockSample} onClose={() => {}} />);

      // with selectedResult={mockSample}, setTemplateCreationContext will be the default option
      const createTemplateSpy = jest.spyOn(
        getRootStore().createStore,
        "setTemplateCreationContext"
      );

      await user.click(
        screen.getByRole("button", {
          name: /CREATE/i,
        })
      );
      expect(createTemplateSpy).toHaveBeenCalledTimes(1);
    });

    test("Create button calls the right action/method (createNewSampleFromTemplate case)", async () => {
      const user = userEvent.setup();
      render(<Dialog selectedResult={mockTemplate} onClose={() => {}} />);

      // with selectedResult={mockTemplate}, createNewSample will be the default option
      const createSampleFromTemplateSpy = jest.spyOn(
        getRootStore().searchStore,
        "createNewSample"
      );

      await user.click(
        screen.getByRole("button", {
          name: /CREATE/i,
        })
      );
      expect(createSampleFromTemplateSpy).toHaveBeenCalledTimes(1);
      expect(createSampleFromTemplateSpy).toHaveBeenCalledWith();
    });

    test("Create button calls the right action/method (split subSample case)", async () => {
      const user = userEvent.setup();
      render(<Dialog selectedResult={mockSubSample} onClose={() => {}} />);

      // with selectedResult={mockSubSample}, splitRecord will be the default option. that method is on search.
      const splitSubSampleSpy = jest.spyOn(
        getRootStore().searchStore.search,
        "splitRecord"
      );

      await user.click(
        screen.getByRole("button", {
          name: /CREATE/i,
        })
      );
      expect(splitSubSampleSpy).toHaveBeenCalledTimes(1);
    });
  });
});
