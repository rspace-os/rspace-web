/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */

import { type Attachment } from "../../Attachment";

export const mockAttachment = ({
  setImageLink,
}: {|
  setImageLink?: () => Promise<void>,
|}): Attachment => {
  return {
    id: 1,
    name: "",
    globalId: "IF1",
    iconName: "",
    recordTypeLabel: "",
    owner: null,
    deleted: false,
    cardTypeLabel: "",
    permalinkURL: null,
    recordDetails: {},
    imageLink: null,
    loadingImage: false,
    loadingString: false,
    chemicalString: "",
    file: null,
    removed: false,
    getFile: () => Promise.reject<File>(),
    remove: () => {},
    download: () => Promise.resolve(),
    createChemicalPreview: () => Promise.resolve(),
    revokeChemicalPreview: () => {},
    setImageLink: setImageLink ?? (() => Promise.resolve()),
    revokeAuthenticatedLink: () => {},
    isChemicalFile: true,
    chemistrySupported: true,
    previewSupported: true,
  };
};
