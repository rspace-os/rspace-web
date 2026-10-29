import "@/modules/common/styles/index.css";
import { Form, useForm } from "@formisch/react";
import { cleanup, render } from "@testing-library/react";
import * as v from "valibot";
import { afterEach, describe, expect, test } from "vitest";
import { page, userEvent } from "vitest/browser";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { RenderFieldsPage } from "./pageObjects/RenderFieldsPage";
import { RenderFields } from "./RenderFields";
import { CardSelectStory, RadioSelectStory, RenderFieldsStory } from "./RenderFields.story";

const form = new RenderFieldsPage();

function labelBounds(control: Element): DOMRect {
  const label = (control as HTMLInputElement).labels?.item(0);
  if (!label) throw new Error("Expected the control to have an associated label");
  return label.getBoundingClientRect();
}

type FocusValidationDocument = {
  id: string;
  enabled: boolean;
  subscription: string;
  status: string;
};

const focusValidationConfig = resolveCollectionConfig<FocusValidationDocument>({
  slug: "focus-validation",
  idField: "id",
  labels: {
    singularKey: "collectionForm.examples.record",
    pluralKey: "collectionForm.examples.records",
  },
  useAsTitle: "id",
  defaultColumns: ["id"],
  fields: [
    { name: "id", type: "text", labelKey: "collectionForm.examples.fields.id", form: false },
    { name: "enabled", type: "boolean", labelKey: "collectionForm.examples.fields.enabled" },
    {
      name: "subscription",
      type: "select",
      labelKey: "collectionForm.examples.fields.status",
      required: true,
      options: ["on", "off"],
      form: { widget: "radio" },
    },
    {
      name: "status",
      type: "select",
      labelKey: "collectionForm.examples.fields.status",
      required: true,
      options: ["draft", "published"],
      form: { widget: "card" },
    },
  ],
});

const focusValidationSchema = v.object({
  id: v.string(),
  enabled: v.pipe(
    v.boolean(),
    v.check((value) => value, "Enable the control."),
  ),
  subscription: v.pipe(
    v.string(),
    v.check((value) => value === "on", "Choose On."),
  ),
  status: v.pipe(
    v.string(),
    v.check((value) => value === "published", "Choose Published."),
  ),
});

const outsideFieldsLabel = "Outside fields";

function FocusValidationFixture({ validate }: { validate: "touch" | "blur" }) {
  const form = useForm({
    schema: focusValidationSchema,
    initialInput: { id: "RS-1", enabled: false, subscription: "off", status: "draft" },
    validate,
  });

  return (
    <Form of={form} onSubmit={() => undefined}>
      <RenderFields fields={focusValidationConfig.fields} form={form} />
      <button type="button">{outsideFieldsLabel}</button>
    </Form>
  );
}

afterEach(() => {
  cleanup();
});

describe("RenderFields focus and blur validation", () => {
  test("validates checkbox, radio, and card controls on focus in touch mode", async () => {
    render(<FocusValidationFixture validate="touch" />);

    await userEvent.tab();
    await expect.element(page.getByText("Enable the control.")).toBeVisible();

    await userEvent.tab();
    await expect.element(page.getByText("Choose On.")).toBeVisible();

    await userEvent.tab();
    await expect.element(page.getByText("Choose Published.")).toBeVisible();
  });

  test("revalidates the unchanged checkbox and choices when they blur", async () => {
    render(<FocusValidationFixture validate="blur" />);

    const enabled = page.getByRole("checkbox", { name: "Enabled" });
    await expect.element(enabled).not.toBeChecked();
    await expect.element(page.getByText("Enable the control.")).not.toBeInTheDocument();

    await userEvent.tab();
    await expect.element(page.getByText("Enable the control.")).not.toBeInTheDocument();
    await userEvent.tab();
    await expect.element(page.getByText("Enable the control.")).toBeVisible();
    await expect.element(enabled).not.toBeChecked();

    await userEvent.tab();
    await expect.element(page.getByText("Choose On.")).toBeVisible();
    await userEvent.tab();
    await expect.element(page.getByText("Choose Published.")).toBeVisible();
  });
});

describe("RenderFields", () => {
  test("renders the configured fields accessibly", async () => {
    render(<RenderFieldsStory />);

    await expect.element(form.recordDetailsSection).toBeVisible();
    await expect.element(form.relationshipsSection).toBeVisible();
    await expect.element(form.hiddenId).not.toBeInTheDocument();
    await expectNoAxeViolations();
  });

  test("writes user changes to the form", async () => {
    render(<RenderFieldsStory />);

    await form.setTitle("Updated title");
    await form.setScore("17");
    await form.toggleEnabled();
    await form.chooseStatus("Published");

    await expect.element(form.values).toHaveTextContent('"title":"Updated title"');
    await expect.element(form.values).toHaveTextContent('"score":17');
    await expect.element(form.values).toHaveTextContent('"enabled":false');
    await expect.element(form.values).toHaveTextContent('"status":"published"');
  });

  test("renders string, object, and React select options", async () => {
    render(<RenderFieldsStory />);

    await form.openStatus();

    await expect.element(form.constantStatusOption).toBeVisible();
    await expect.element(form.objectStatusOption).toBeVisible();
    await expect.element(form.richStatusOption).toBeVisible();
  });

  test("filters select options as the user types", async () => {
    render(<RenderFieldsStory />);

    await form.typeStatus("Pub");

    await expect.element(form.richStatusOption).toBeVisible();
    await expect.element(form.constantStatusOption).not.toBeInTheDocument();
  });

  test("reactively applies field conditions", async () => {
    render(<RenderFieldsStory />);

    await expect.element(form.notes).toBeVisible();
    await form.toggleEnabled();
    await expect.element(form.notes).not.toBeInTheDocument();
    await form.toggleEnabled();
    await expect.element(form.notes).toBeVisible();
    await form.setNotes("hide");
    await expect.element(form.notes).not.toBeInTheDocument();
  });

  test("reactively shows sections by their group condition", async () => {
    render(<RenderFieldsStory presentation="conditional" />);

    await expect.element(form.relationshipsSection).toBeVisible();
    await form.toggleEnabled();
    await expect.element(form.relationshipsSection).not.toBeInTheDocument();
    await form.toggleEnabled();
    await expect.element(form.relationshipsSection).toBeVisible();
    await expect.element(form.owner).toHaveValue("Ada Lovelace");
  });

  test("closes up a row when one of its fields is hidden", async () => {
    render(<RenderFieldsStory presentation="conditional" />);
    const visibleRowItems = () =>
      [...document.querySelectorAll("[data-slot=field-row-item]")].filter(
        (item) => getComputedStyle(item).display !== "none",
      ).length;

    await expect.element(form.score).not.toBeInTheDocument();
    await expect.poll(visibleRowItems).toBe(1);
    await form.chooseStatus("In review");
    await expect.element(form.score).toBeVisible();
    await expect.poll(visibleRowItems).toBe(2);
  });

  test("associates descriptions and validation errors with their controls", async () => {
    render(<RenderFieldsStory />);

    await expect.element(form.notes).toHaveAccessibleDescription("The human-readable name of the record.");
    await form.setTitle("");
    await expect.element(form.title).toBeInvalid();
    await expect.element(form.title).toHaveAccessibleDescription("Title is required.");
  });

  test("disables every rendered control", async () => {
    render(<RenderFieldsStory disabled />);

    await expect.element(form.title).toBeDisabled();
    await expect.element(form.notes).toBeDisabled();
    await expect.element(form.score).toBeDisabled();
    await expect.element(form.enabled).toBeDisabled();
    await expect.element(form.modifiedAt).toBeDisabled();
    await expect.element(form.status).toBeDisabled();
    await expect.element(form.owner).toBeDisabled();
    await expect.element(form.collaborators).toBeDisabled();
  });

  test("uses a wider label column and keeps descriptions beneath inline controls", async () => {
    render(
      <div style={{ width: 900 }}>
        <RenderFieldsStory presentation="item-details" />
      </div>,
    );

    await expect
      .poll(() => {
        const title = form.title.element().getBoundingClientRect();
        const titleLabel = labelBounds(form.title.element());
        const notes = form.notes.element().getBoundingClientRect();
        const description = form.notesDescription.element().getBoundingClientRect();
        return {
          descriptionAlignedWithControl: Math.abs(description.left - notes.left) < 1,
          descriptionBelowControl: description.top >= notes.bottom,
          labelBesideControl: titleLabel.right <= title.left,
          wideLabelColumn: title.left - titleLabel.left >= 220,
        };
      })
      .toEqual({
        descriptionAlignedWithControl: true,
        descriptionBelowControl: true,
        labelBesideControl: true,
        wideLabelColumn: true,
      });
    await expect.element(form.notes).toHaveAccessibleDescription("The human-readable name of the record.");
    await expectNoAxeViolations();
  });

  test("stacks inline fields when their container is narrow", async () => {
    render(
      <div style={{ width: 420 }}>
        <RenderFieldsStory presentation="item-details" />
      </div>,
    );

    await expect
      .poll(() => {
        const title = form.title.element().getBoundingClientRect();
        const titleLabel = labelBounds(form.title.element());
        return {
          aligned: Math.abs(titleLabel.left - title.left) < 1,
          stacked: titleLabel.bottom <= title.top,
        };
      })
      .toEqual({ aligned: true, stacked: true });
  });
});

describe("RenderFields card select", () => {
  test("renders rich option labels in an accessible responsive group", async () => {
    render(<CardSelectStory />);

    await expect.element(form.cardStatusGroup).toHaveClass("grid-cols-1", "sm:grid-cols-2", "lg:grid-cols-3");
    await expect.element(form.cardStatus("Draft")).toBeChecked();
    await expect.element(form.cardStatus("Published")).toBeDisabled();
    await expect.element(form.cardStatus("Archived")).toBeDisabled();
    expect(getComputedStyle(form.cardStatus("Draft").element()).backgroundColor).not.toBe(
      getComputedStyle(form.cardStatus("In review").element()).backgroundColor,
    );
    await expectNoAxeViolations();
  });

  test("selects cards with the keyboard and reacts to the complete form input", async () => {
    render(<CardSelectStory />);

    await form.chooseNextCardStatus("Draft");
    await expect.element(form.cardStatus("In review")).toBeChecked();
    await expect.element(form.values).toHaveTextContent('"status":"review"');

    await form.toggleEnabled();
    await expect.element(form.cardStatus("Published")).not.toBeDisabled();
    await expect.element(form.cardStatus("Archived")).toBeDisabled();
    await form.chooseCardStatus("Published");
    await expect.element(form.cardStatus("Published")).toBeChecked();
    await expect.element(form.values).toHaveTextContent('"status":"published"');
  });
});

describe("RenderFields radio select", () => {
  test("renders compact labelled choices and updates the form from the visible option label", async () => {
    render(<RadioSelectStory />);

    await expect.element(form.subscriptionGroup).toHaveAccessibleName("Status");
    await expect.element(form.subscriptionGroup).toHaveAccessibleDescription("The human-readable name of the record.");
    await expect.element(form.subscriptionOption("On")).toBeChecked();
    await expect.element(form.subscriptionOption("Off")).toBeDisabled();

    await form.toggleAllowOff();
    await expect.element(form.subscriptionOption("Off")).not.toBeDisabled();
    await form.chooseSubscription("Off");

    await expect.element(form.subscriptionOption("Off")).toBeChecked();
    await expect.element(form.values).toHaveTextContent('"subscription":"off"');
    await expectNoAxeViolations();
  });

  test("does not change a read-only radio choice", async () => {
    render(<RadioSelectStory readOnly />);

    await form.toggleAllowOff();
    await form.chooseSubscription("Off");
    await expect.element(form.subscriptionOption("On")).toBeChecked();
  });

  test("disables all radio choices when the field is disabled", async () => {
    render(<RadioSelectStory disabled />);

    await expect.element(form.subscriptionOption("On")).toBeDisabled();
    await expect.element(form.subscriptionOption("Off")).toBeDisabled();
  });
});
