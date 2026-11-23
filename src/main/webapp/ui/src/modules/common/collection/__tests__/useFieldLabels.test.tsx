import { act, render, screen } from "@testing-library/react";
import { createInstance } from "i18next";
import ICU from "i18next-icu";
import { I18nextProvider, initReactI18next } from "react-i18next";
import { describe, expect, it } from "vitest";
import { useFieldLabels } from "../useFieldLabels";

const ownerField = {
  labelKey: "tableList.targetFields.name" as const,
  derivedLabel: { key: "common:tableList.targetFields.name" as const },
  origin: { via: { name: "owner", label: { key: "common:tableList.fields.location" as const } } },
};

const emptyOwnerField = {
  labelKey: "tableList.targetFields.name" as const,
  derivedLabel: { key: "common:tableList.targetFields.name" as const },
  origin: { via: { name: "owner", label: { text: "" } } },
};

function LabelHarness() {
  const { fieldLabel, fieldOptionLabel } = useFieldLabels();

  return (
    <dl>
      <dt>{"qualified"}</dt>
      <dd>{fieldLabel(ownerField)}</dd>
      <dt>{"qualified other namespace"}</dt>
      <dd>{fieldLabel({ labelKey: "about:copyright" })}</dd>
      <dt>{"empty owner"}</dt>
      <dd>{fieldLabel(emptyOwnerField)}</dd>
      <dt>{"source"}</dt>
      <dd>{fieldOptionLabel({ ...ownerField, origin: { ...ownerField.origin, sourceLabel: "Template" } })}</dd>
      <dt>{"literal"}</dt>
      <dd>{fieldLabel({ labelKey: "tableList.targetFields.name", derivedLabel: { text: "targetFields.name" } })}</dd>
      <dt>{"empty override"}</dt>
      <dd>
        {fieldLabel({ labelKey: "tableList.examples.fields.title", label: "", derivedLabel: { text: "Ignored" } })}
      </dd>
    </dl>
  );
}

async function createI18n() {
  const i18n = createInstance();
  await i18n
    .use(ICU)
    .use(initReactI18next)
    .init({
      lng: "en-US",
      fallbackLng: false,
      defaultNS: "common",
      ns: ["common", "about"],
      interpolation: { escapeValue: false },
      resources: {
        "en-US": {
          common: {
            tableList: {
              fields: {
                hierarchicalLabel: "{owner} → {field}",
                labelWithSource: "{label} ({source})",
                location: "Instrument",
              },
              targetFields: { name: "Name" },
            },
          },
          about: { copyright: "Page title" },
        },
        "fr-FR": {
          common: {
            tableList: {
              fields: {
                hierarchicalLabel: "{owner} → {field}",
                labelWithSource: "{label} ({source})",
                location: "Instrument de réservation",
              },
              targetFields: { name: "Nom" },
            },
          },
          about: { copyright: "Titre de page" },
        },
      },
    });
  return i18n;
}

describe("useFieldLabels", () => {
  it("translates qualified descriptors and preserves literal overrides", async () => {
    const i18n = await createI18n();
    render(
      <I18nextProvider i18n={i18n}>
        <LabelHarness />
      </I18nextProvider>,
    );

    expect(screen.getByText("Instrument → Name")).toBeInTheDocument();
    expect(screen.getByText("Page title")).toBeInTheDocument();
    expect(screen.getByText("empty owner").nextElementSibling).toHaveTextContent("Name");
    expect(screen.getByText("Instrument → Name (Template)")).toBeInTheDocument();
    expect(screen.getByText("targetFields.name")).toBeInTheDocument();
    expect(screen.getByText("empty override").nextElementSibling).toBeEmptyDOMElement();

    await act(async () => {
      await i18n.changeLanguage("fr-FR");
    });

    expect(screen.getByText("Instrument de réservation → Nom")).toBeInTheDocument();
    expect(screen.getByText("Titre de page")).toBeInTheDocument();
    expect(screen.getByText("empty owner").nextElementSibling).toHaveTextContent("Nom");
    expect(screen.getByText("Instrument de réservation → Nom (Template)")).toBeInTheDocument();
    expect(screen.getByText("targetFields.name")).toBeInTheDocument();
    expect(screen.getByText("empty override").nextElementSibling).toBeEmptyDOMElement();
  });
});
