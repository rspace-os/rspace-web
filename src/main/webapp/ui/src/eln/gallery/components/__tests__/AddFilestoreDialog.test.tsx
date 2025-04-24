/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, waitFor, act } from "@testing-library/react";
import "@testing-library/jest-dom";
import userEvent from "@testing-library/user-event";
import AddFilestoreDialog from "../AddFilestoreDialog";
import { axe, toHaveNoViolations } from "jest-axe";
import axios from "@/common/axios";
import MockAdapter from "axios-mock-adapter";
import browseFilesystem1 from "./browseFilesystem1.json";

expect.extend(toHaveNoViolations);

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("AddFilestoreDialog", () => {
  test("Should have no axe violations.", async () => {
    const user = userEvent.setup();

    mockAxios.onGet("/userform/ajax/inventoryOauthToken").reply(200, {
      data: "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAiLCJpYXQiOjE3MzQzNDI5NTYsImV4cCI6MTczNDM0NjU1NiwicmVmcmVzaFRva2VuSGFzaCI6ImZlMTVmYTNkNWUzZDVhNDdlMzNlOWUzNDIyOWIxZWEyMzE0YWQ2ZTZmMTNmYTQyYWRkY2E0ZjE0Mzk1ODJhNGQifQ.HCKre3g_P1wmGrrrnQncvFeT9pAePFSc4UPuyP5oehI",
    });

    mockAxios.onGet("/api/v1/gallery/filesystems").reply(200, [
      {
        id: 1,
        name: "irods test",
        url: "irods-test.researchspace.com",
        clientType: "IRODS",
        authType: "PASSWORD",
        options: {},
        loggedAs: null,
      },
    ]);

    mockAxios
      .onGet("/api/v1/gallery/filesystems/1/browse?remotePath=/")
      .reply(200, browseFilesystem1);

    const { baseElement } = render(
      <AddFilestoreDialog open onClose={() => {}} />
    );

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeVisible();
    });
    const radio = await screen.findByRole("radio", { name: /irods test/i });

    expect(await axe(baseElement)).toHaveNoViolations();

    await act(async () => {
      await user.click(radio);
    });

    await act(async () => {
      await user.click(
        screen.getByRole("button", { name: /choose filesystem/i })
      );
    });

    const treeitem = await screen.findByRole("treeitem", { name: /^test$/i });

    expect(await axe(baseElement)).toHaveNoViolations();

    await act(async () => {
      await user.click(treeitem);
    });

    expect(await axe(baseElement)).toHaveNoViolations();
  });
});
