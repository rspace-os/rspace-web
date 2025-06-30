/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
/* eslint-disable no-undefined */
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import AttachmentField from "../AttachmentField";
import TextField from "@mui/material/TextField";
import FileField from "../FileField";
import { ExistingAttachment } from "../../../stores/models/AttachmentModel";
import each from "jest-each";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
import { containerAttrs } from "../../../stores/models/__tests__/ContainerModel/mocking";
import ContainerModel from "../../../stores/models/ContainerModel";
import MemoisedFactory from "../../../stores/models/Factory/MemoisedFactory";
import type { Attachment } from "../../../stores/definitions/Attachment";

jest.mock("@mui/material/TextField", () => jest.fn(() => <div></div>));
jest.mock("../FileField", () => jest.fn(() => <div></div>));
jest.mock("../../Ketcher/KetcherDialog", () => jest.fn(() => <div></div>));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

const expectLabel = (text: string) => (container: HTMLElement) =>
  expect(container).toHaveTextContent(text);

const expectTextField = (value: string) => () =>
  expect(TextField).toHaveBeenCalledWith(
    expect.objectContaining({ value }),
    expect.anything()
  );

const activeResult = new ContainerModel(new MemoisedFactory(), {
  ...containerAttrs(),
  cType: "LIST",
});

const makeAttachment = (attrs?: { name: string }) =>
  new ExistingAttachment(
    {
      id: 0,
      name: "",
      size: 0,
      globalId: "IF0",
      contentMimeType: "",
      _links: [],
      ...attrs,
    },
    "",
    () => {}
  );

describe("AttachmentField", () => {
  describe("Description field", () => {
    each`
      disabled     | value    | noValueLabel | expectFn
      ${true}      | ${""}    | ${undefined} | ${expectLabel("No description")}
      ${true}      | ${""}    | ${"foo"}     | ${expectLabel("foo")}
      ${true}      | ${"bar"} | ${undefined} | ${expectTextField("bar")}
      ${true}      | ${"bar"} | ${"foo"}     | ${expectTextField("bar")}
      ${false}     | ${""}    | ${undefined} | ${expectTextField("")}
      ${false}     | ${""}    | ${"foo"}     | ${expectTextField("")}
      ${false}     | ${"bar"} | ${undefined} | ${expectTextField("bar")}
      ${false}     | ${"bar"} | ${"foo"}     | ${expectTextField("bar")}
      ${undefined} | ${""}    | ${undefined} | ${expectTextField("")}
      ${undefined} | ${""}    | ${"foo"}     | ${expectTextField("")}
      ${undefined} | ${"bar"} | ${undefined} | ${expectTextField("bar")}
      ${undefined} | ${"bar"} | ${"foo"}     | ${expectTextField("bar")}
    `.test(
      '$# {disabled = $disabled, value = "$value", noValueLabel = $noValueLabel}',
      ({
        disabled,
        value,
        noValueLabel,
        expectFn,
      }: {
        disabled: undefined | boolean;
        value: string;
        noValueLabel: undefined | string;
        expectFn: (container: HTMLElement) => void;
      }) => {
        const { container } = render(
          <ThemeProvider theme={materialTheme}>
            <AttachmentField
              attachment={null}
              fieldOwner={activeResult}
              onAttachmentChange={() => {}}
              onChange={() => {}}
              value={value}
              disabled={disabled}
              noValueLabel={noValueLabel}
            />
          </ThemeProvider>
        );
        expectFn(container);
      }
    );
  });
  describe("Help text", () => {
    describe('value = ""', () => {
      test("Help text is shown.", () => {
        const { container } = render(
          <ThemeProvider theme={materialTheme}>
            <AttachmentField
              attachment={null}
              fieldOwner={activeResult}
              onAttachmentChange={() => {}}
              onChange={() => {}}
              value=""
            />
          </ThemeProvider>
        );
        expect(container).toHaveTextContent(
          "A file of any type can be attached (e.g. image, document, or chemistry file)"
        );
      });
    });
    describe('value = "foo"', () => {
      test("Help text is not shown.", () => {
        const { container } = render(
          <ThemeProvider theme={materialTheme}>
            <AttachmentField
              attachment={null}
              fieldOwner={activeResult}
              onAttachmentChange={() => {}}
              onChange={() => {}}
              value="foo"
            />
          </ThemeProvider>
        );
        expect(container).not.toHaveTextContent(
          "A file of any type can be attached (e.g. image, document, or chemistry file)"
        );
      });
    });
  });
  describe("File Selector", () => {
    each`
      disableFileUpload | attachment          | showFileField | showNoAttachmentLabel
      ${true}           | ${null}             | ${false}      | ${false}
      ${true}           | ${makeAttachment()} | ${false}      | ${false}
      ${false}          | ${null}             | ${true}       | ${true}
      ${false}          | ${makeAttachment()} | ${true}       | ${false}
      ${undefined}      | ${null}             | ${true}       | ${true}
      ${undefined}      | ${makeAttachment()} | ${true}       | ${false}
    `.describe(
      "$# {disableFileUpload = $disableFileUpload, attachment }",
      ({
        disableFileUpload,
        attachment,
        showFileField,
        showNoAttachmentLabel,
      }: {
        disableFileUpload: undefined | boolean;
        attachment: Attachment | null;
        showFileField: boolean;
        showNoAttachmentLabel: boolean;
      }) => {
        let textContent: string | null;
        function renderAttachmentField(): void {
          const { container } = render(
            <ThemeProvider theme={materialTheme}>
              <AttachmentField
                attachment={attachment}
                fieldOwner={activeResult}
                onAttachmentChange={() => {}}
                onChange={() => {}}
                value=""
                disableFileUpload={disableFileUpload}
              />
            </ThemeProvider>
          );
          textContent = container.textContent;
        }
        test("Whether to show FileField.", () => {
          renderAttachmentField();
          if (showFileField) {
            expect(FileField).toHaveBeenCalled();
          } else {
            expect(FileField).not.toHaveBeenCalled();
          }
        });
        test('Whether to show "No File Attached" label.', () => {
          renderAttachmentField();
          if (showNoAttachmentLabel) {
            expect(textContent).toEqual(
              expect.stringContaining("No File Attached")
            );
          } else {
            expect(textContent).not.toEqual(
              expect.stringContaining("No File Attached")
            );
          }
        });
      }
    );
  });
  describe("File viewer", () => {
    describe("attachment = ExistingAttachment", () => {
      test("Attachment's filename should be shown.", () => {
        const { container } = render(
          <ThemeProvider theme={materialTheme}>
            <AttachmentField
              attachment={makeAttachment({ name: "image.png" })}
              fieldOwner={activeResult}
              onAttachmentChange={() => {}}
              onChange={() => {}}
              value=""
            />
          </ThemeProvider>
        );
        expect(container).toHaveTextContent("image.png");
      });
    });
  });
});
