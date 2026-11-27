import { act, cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import fc, { type Arbitrary } from "fast-check";
import { HttpResponse, http } from "msw/http";
import { describe, expect, onTestFinished, test, vi } from "vitest";
import ExportRepo, { repositoryFormValidator } from "../ExportRepo";
import { DEFAULT_EXPORT_FORM_VALUES, type ExportForm } from "../exportForm";
import funders from "./funders.json";
import repoList from "./repoList.json";
import zenodoRepoList from "./zenodoRepoList.json";
import "@/__tests__/__mocks__/matchMedia";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { server } from "@/__tests__/mswServer";
import type { Repo } from "../repositories/common";
import type { Tag } from "../repositories/Tags";
import WithExportForm from "./WithExportForm";

type DMP = {
  dmpUserInternalId: number;
  dmpTitle: string;
  dmpId: string;
};

window.fetch = vi.fn(() =>
  Promise.resolve({
    status: 200,
    ok: true,
    json: () => Promise.resolve(funders),
  } as Response),
);
const props = {
  repoList: repoList.map((repo) => ({
    ...repo,
    repoCfg: -1,
  })),
  fetchTags: vi.fn().mockResolvedValue([] as Array<Tag>),
};

/*
 * Renders ExportRepo as the export dialog does: inside the FormGroup that
 * validates it, followed by a Next button that submits that group.
 */
function RepositoryPane({
  repoList,
  fetchTags,
  showPane = true,
  onNext,
  onInvalid,
  formRef,
}: {
  repoList: Array<Repo>;
  fetchTags: () => Promise<Array<Tag>>;
  // false unmounts the pane, as going back to an earlier pane does
  showPane?: boolean;
  onNext?: () => void;
  onInvalid?: () => void;
  formRef?: { current: ExportForm | null };
}) {
  return (
    <WithExportForm
      values={{ exportConfig: { ...DEFAULT_EXPORT_FORM_VALUES.exportConfig, repository: true, repoData: repoList } }}
      formRef={formRef}
    >
      {(form) =>
        showPane && (
          <form.FormGroup
            name="repositoryForm"
            validators={{ onSubmit: repositoryFormValidator(repoList) }}
            onGroupSubmit={onNext}
            onGroupSubmitInvalid={onInvalid}
          >
            {(group) => (
              <>
                <ExportRepo
                  form={form}
                  errors={group.state.meta.submissionAttempts > 0 ? group.state.meta.errorMap.onSubmit : undefined}
                  fetchTags={fetchTags}
                />
                <button type="button" aria-label="Next" onClick={() => void group.handleSubmit()} />
              </>
            )}
          </form.FormGroup>
        )
      }
    </WithExportForm>
  );
}

describe("ExportRepo", () => {
  test("Should display selection message", async () => {
    render(<RepositoryPane {...props} />);
    await waitFor(() => {
      expect(props.fetchTags).toHaveBeenCalled();
    });
    expect(screen.getByText("workspace:export.repositories.choiceLabel")).toBeInTheDocument();
  });
  test("Should display repository list with two repositories", async () => {
    render(<RepositoryPane {...props} />);
    await waitFor(() => {
      expect(props.fetchTags).toHaveBeenCalled();
    });
    expect(screen.getByText("Repository 1")).toBeInTheDocument();
    expect(screen.getByText("Repository 2")).toBeInTheDocument();
  });

  test("Should have first repository in list selected", async () => {
    render(<RepositoryPane {...props} />);
    await waitFor(() => {
      expect(props.fetchTags).toHaveBeenCalled();
    });
    const repo1RadioButton = screen.getByTestId<HTMLInputElement>("radio-button-app.repo1");
    expect(repo1RadioButton).toBeChecked();
  });
  const arbListOfDMPs = (n: number): Arbitrary<Array<DMP>> =>
    fc.array(
      fc.record<DMP>({
        dmpUserInternalId: fc.nat(),
        dmpTitle: fc.string(),
        dmpId: fc.string(),
      }),
      { maxLength: n, minLength: n },
    );
  const arbSetOfIndexes = (min: number, max: number): Arbitrary<Set<number>> =>
    fc
      .uniqueArray(fc.nat(max - 1), { minLength: min, maxLength: max })

      .map((arr) => new Set(arr));
  test(
    "If chosen repository is Zenodo and more than 1 DMP is selected then a warning should be shown.",
    async () => {
      await fc.assert(
        fc
          .asyncProperty(
            // limit of 10 DMPs is set to cap memory usage
            fc
              .integer({ min: 2, max: 10 })
              .chain((n) => fc.tuple<[Set<number>, Array<DMP>]>(arbSetOfIndexes(2, n), arbListOfDMPs(n)))
              .filter(([, linkedDMPs]) => {
                const ids = new Set(linkedDMPs.map((dmp) => dmp.dmpUserInternalId));
                return ids.size === linkedDMPs.length;
              }),
            async ([indexes, linkedDMPs]) => {
              const user = userEvent.setup();
              const generatedRepoList = [{ ...zenodoRepoList[0], linkedDMPs, repoCfg: -1 }];
              render(<RepositoryPane repoList={generatedRepoList} fetchTags={props.fetchTags} />);
              await waitFor(() => {
                expect(props.fetchTags).toHaveBeenCalled();
              });
              const repoChoice = await screen.findByRole("radiogroup", {
                name: "workspace:export.repositories.labels.choice",
              });
              expect(
                await within(repoChoice).findByRole("radio", {
                  name: "Zenodo",
                }),
              ).toBeChecked();
              await user.click(
                await screen.findByRole("checkbox", {
                  name: "workspace:export.repositories.dmp.associateLabel",
                }),
              );

              const dmpTable = await screen.findByRole("table");
              const checkboxes = await within(dmpTable).findAllByRole("checkbox", {
                name: "apps:dmpIntegrations.dialog.selectPlanLabel",
              });
              for (const [index, checkbox] of checkboxes.entries()) {
                if (indexes.has(index)) await user.click(checkbox);
              }
              expect(await screen.findByRole("alert")).toHaveTextContent(
                "workspace:export.repositories.dmp.zenodoLimit",
              );
            },
          )
          .beforeEach(() => {
            cleanup();
            props.fetchTags.mockClear();
          }),
        { numRuns: 1 },
      );
    },
    30 * 1000,
  );
  test("Zenodo's placeholder people and subject do not leak into Dataverse, also after Back and Next.", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("/directory/ajax/subject", () =>
        HttpResponse.json({ fullName: "Test User", email: "test@example.com" }),
      ),
    );
    const zenodoThenDataverse = [
      { ...zenodoRepoList[0], repoCfg: -1 },
      { ...repoList[0], repoName: "app.dataverse", displayName: "Dataverse", repoCfg: -1 },
    ];
    const onNext = vi.fn();
    const onInvalid = vi.fn();
    const formRef: { current: ExportForm | null } = { current: null };
    const pane = (showPane: boolean) => (
      <RepositoryPane
        repoList={zenodoThenDataverse}
        fetchTags={vi.fn().mockResolvedValue([])}
        showPane={showPane}
        onNext={onNext}
        onInvalid={onInvalid}
        formRef={formRef}
      />
    );
    const switchFromZenodoToDataverse = async () => {
      onInvalid.mockClear();
      await user.click(screen.getByRole("radio", { name: "Zenodo" }));
      // Title and description are valid, so only Dataverse's missing subject can fail validation.
      await user.type(screen.getByRole("textbox", { name: "workspace:export.repositories.common.title" }), "Title");
      await user.type(
        screen.getByRole("textbox", { name: "workspace:export.repositories.common.description" }),
        "Description",
      );
      await user.click(screen.getByRole("radio", { name: "Dataverse" }));
      // The current user is the default author and contact.
      expect(await screen.findAllByText(/^Test User - /)).toHaveLength(2);
      expect(screen.queryByText(/DUMMY_VALUE/)).not.toBeInTheDocument();
      expect(JSON.stringify(formRef.current?.state.values)).not.toContain("DUMMY_VALUE");
      await user.click(screen.getByRole("button", { name: "Next" }));
      await waitFor(() => expect(onInvalid).toHaveBeenCalledTimes(1));
      expect(onNext).not.toHaveBeenCalled();
    };

    const { rerender } = render(pane(true));
    await switchFromZenodoToDataverse();

    // Back then Next unmounts the pane and mounts it again over the same form.
    rerender(pane(false));
    rerender(pane(true));
    await switchFromZenodoToDataverse();
  });
  test("Back then Next keeps the tags the user edited instead of loading them again.", async () => {
    const loadedTag: Tag = {
      value: "loaded",
      vocabulary: "vocabulary",
      uri: "https://example.com/loaded",
      version: "1",
    };
    const fetchTags = vi.fn().mockResolvedValue([loadedTag]);
    const formRef: { current: ExportForm | null } = { current: null };
    const pane = (showPane: boolean) => (
      <RepositoryPane
        repoList={[{ ...zenodoRepoList[0], repoCfg: -1 }]}
        fetchTags={fetchTags}
        showPane={showPane}
        formRef={formRef}
      />
    );
    const { rerender } = render(pane(true));
    await waitFor(() => expect(formRef.current?.state.values.repositoryForm.tags).toEqual([loadedTag]));

    // the user removes the loaded tag
    act(() => formRef.current?.setFieldValue("repositoryForm.tags", []));
    // Back then Next unmounts the pane and mounts it again over the same form.
    rerender(pane(false));
    rerender(pane(true));
    await screen.findByRole("radio", { name: "Zenodo" });

    expect(fetchTags).toHaveBeenCalledTimes(1);
    expect(formRef.current?.state.values.repositoryForm.tags).toEqual([]);
  });
  test("A failed tag lookup is tried again on the next visit to the pane.", async () => {
    onTestFinished(silenceConsole(["error"], ["Tags unavailable"]));
    const loadedTag: Tag = {
      value: "loaded",
      vocabulary: "vocabulary",
      uri: "https://example.com/loaded",
      version: "1",
    };
    const fetchTags = vi.fn().mockRejectedValueOnce(new Error("Tags unavailable")).mockResolvedValue([loadedTag]);
    const formRef: { current: ExportForm | null } = { current: null };
    const pane = (showPane: boolean) => (
      <RepositoryPane
        repoList={[{ ...zenodoRepoList[0], repoCfg: -1 }]}
        fetchTags={fetchTags}
        showPane={showPane}
        formRef={formRef}
      />
    );
    const { rerender } = render(pane(true));
    await waitFor(() => expect(fetchTags).toHaveBeenCalledTimes(1));
    await screen.findByRole("radio", { name: "Zenodo" });

    rerender(pane(false));
    rerender(pane(true));

    await waitFor(() => expect(formRef.current?.state.values.repositoryForm.tags).toEqual([loadedTag]));
    expect(fetchTags).toHaveBeenCalledTimes(2);
  });
  test("Tags loaded for another format are cleared, and forgotten, when loading them for this one fails.", async () => {
    onTestFinished(silenceConsole(["error"], ["Tags unavailable"]));
    const htmlTag: Tag = { value: "html", vocabulary: "vocabulary", uri: "https://example.com/html", version: "1" };
    const fetchTags = vi.fn().mockRejectedValue(new Error("Tags unavailable"));
    const repoList = [{ ...zenodoRepoList[0], repoCfg: -1 }];
    const formRef: { current: ExportForm | null } = { current: null };
    render(
      <WithExportForm
        values={{
          exportConfig: {
            ...DEFAULT_EXPORT_FORM_VALUES.exportConfig,
            archiveType: "pdf",
            repository: true,
            repoData: repoList,
          },
          repositoryForm: { ...DEFAULT_EXPORT_FORM_VALUES.repositoryForm, tags: [htmlTag], tagsLoadedFor: "html" },
        }}
        formRef={formRef}
      >
        {(form) => <ExportRepo form={form} errors={undefined} fetchTags={fetchTags} />}
      </WithExportForm>,
    );

    await waitFor(() => expect(fetchTags).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(formRef.current?.state.values.repositoryForm.tags).toEqual([]));
    // nothing is recorded as loaded, so going back to html loads its tags again
    expect(formRef.current?.state.values.repositoryForm.tagsLoadedFor).toBeNull();
  });
  test("Validation reads the latest form values and marks the fields that fail it.", async () => {
    const user = userEvent.setup();
    const onNext = vi.fn();
    const formRef: { current: ExportForm | null } = { current: null };
    render(
      <RepositoryPane
        repoList={[{ ...zenodoRepoList[0], repoCfg: -1 }]}
        fetchTags={vi.fn().mockResolvedValue([])}
        onNext={onNext}
        formRef={formRef}
      />,
    );
    const title = screen.getByRole("textbox", { name: "workspace:export.repositories.common.title" });
    expect(title).toHaveAttribute("aria-invalid", "false");

    await user.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => expect(title).toHaveAttribute("aria-invalid", "true"));
    expect(onNext).not.toHaveBeenCalled();

    await user.type(title, "Title");
    await user.type(
      screen.getByRole("textbox", { name: "workspace:export.repositories.common.description" }),
      "Description",
    );
    await user.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => expect(onNext).toHaveBeenCalledTimes(1));
    expect(title).toHaveAttribute("aria-invalid", "false");
    expect(formRef.current?.state.values.repositoryForm).toMatchObject({ title: "Title", description: "Description" });
  });
});
