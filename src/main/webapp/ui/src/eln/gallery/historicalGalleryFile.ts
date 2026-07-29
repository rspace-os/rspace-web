import type React from "react";
import { filenameExceptExtension, justFilenameExtension } from "../../util/files";
import Result from "../../util/result";
import type { URL as UrlType } from "../../util/types";
import { Description, type GalleryFile, type Id, iconForExtension, idToString } from "./useGalleryListing";

/*
 * One reason shared by every refused action. It reaches the user as the
 * subheader of the disabled Actions menu item, so it is worded for them, and
 * worded for any action rather than for editing: the same reason has to read
 * correctly against Open and Log out as against Rename and Delete.
 */
const LOCKED = "This is a past version, so it is read-only.";

function locked<T>(): Result<T> {
  return Result.Error([new Error(LOCKED)]);
}

/**
 * A Gallery item as it was at one past version, shown by the pinned version
 * view at `/gallery/item/<id>/<version>`.
 *
 * This decorates the live file rather than overlaying a "read-only" flag on it,
 * so the object never reports a version it is not showing: `version`, `name`,
 * `extension`, `description`, `size`, `modificationDate`, `thumbnailUrl` and
 * `downloadHref` all describe version N, and every mutating predicate refuses.
 * Nothing downstream has to remember to check a flag, because the predicate
 * mechanism the Actions menu already consults says no on its own.
 *
 * A new version can replace the file with one of a different name, and the name
 * and description can be edited at any time, so treating any of those as
 * properties of the item rather than of the version shows the wrong metadata
 * beside the right bytes.
 *
 * Two things are deliberately delegated rather than overridden:
 *
 *   - `globalId` stays unversioned (`GL42`, not `GL42v2`). The ELN
 *     linked-documents and inventory referencing lookups read it, and neither
 *     records the version a reference was made against, so a versioned id would
 *     only be discarded. The versioned form is rendered in the InfoPanel for
 *     display and nowhere else. See ADR 0004.
 *   - `canViewVersionHistory`, so the view is not a dead end: the history is how
 *     a user gets from one pinned version to another, or back to the live item.
 */
export class HistoricalGalleryFile implements GalleryFile {
  private readonly file: GalleryFile;

  readonly version: number;
  readonly size: number;
  readonly modificationDate: Date | undefined;

  /*
   * Version N's own filename. Null only if the audit row carried none, in which
   * case the live name is the best available answer.
   */
  private readonly versionName: string | null;

  /*
   * Version N's own description, as an empty description rather than a missing
   * one when the audited revision had none. Never the live item's: that would put
   * today's caption beside an older version's content.
   */
  readonly description: Description;

  constructor({
    file,
    version,
    size,
    modificationDate,
    name,
    description,
  }: {
    file: GalleryFile;
    version: number;
    size: number;
    modificationDate: Date | undefined;
    name: string | null;
    description: string | null;
  }) {
    this.file = file;
    this.version = version;
    this.size = size;
    this.modificationDate = modificationDate;
    this.versionName = name;
    this.description = description ? Description.Present(description) : Description.Empty();
  }

  /** The version this object is pinned to, for UI that has to say so. */
  get pinnedVersion(): number {
    return this.version;
  }

  /*
   * Historical bytes only come from /Streamfile: the API's file endpoint has no
   * version parameter. A same-origin URL, so the download attribute still
   * applies and the file saves under its own name.
   */
  private streamfileUrl(): UrlType {
    return `/Streamfile/${idToString(this.file.id).elseThrow()}?version=${this.version}`;
  }

  readonly downloadHref = (): Promise<UrlType> => Promise.resolve(this.streamfileUrl());

  deconstructor(): void {
    this.file.deconstructor();
  }

  get id(): Id {
    return this.file.id;
  }

  get globalId(): string | undefined {
    return this.file.globalId;
  }

  get name(): string {
    return this.versionName ?? this.file.name;
  }

  get key(): string {
    return this.file.key;
  }

  /*
   * Derived from this version's name rather than delegated, since a renamed
   * version can have a different extension, which decides the icon and which
   * previewers apply. A name with no dot has no extension, rather than being
   * its own extension.
   */
  get extension(): string | null {
    if (this.versionName === null) return this.file.extension;
    return this.versionName.includes(".") ? justFilenameExtension(this.versionName) : null;
  }

  get creationDate(): Date | undefined {
    return this.file.creationDate;
  }

  get type(): string | undefined {
    return this.file.type;
  }

  /*
   * Every thumbnail endpoint serves the live bytes: /gallery/getThumbnail takes
   * no version (its second path segment is a cache-buster, not a selector), and
   * the document and chemistry thumbnails are keyed on the live record. So a
   * delegated thumbnail would show another version's content.
   *
   * An image can stand in for its own thumbnail, since /Streamfile is
   * version-aware. Anything else falls back to the stock icon for its type:
   * showing no content is honest, showing the wrong content is not.
   *
   * ponytail: this fetches the full-size original for a grid tile. Only the one
   * pinned item in a listing is affected. If that becomes too heavy, the fix is a
   * revision-aware thumbnail endpoint, and the decorator already knows enough to
   * address one.
   */
  get thumbnailUrl(): UrlType {
    if (this.isImage) return this.streamfileUrl();
    return iconForExtension(this.extension);
  }

  get ownerId(): number | null {
    return this.file.ownerId;
  }

  get ownerName(): string {
    return this.file.ownerName;
  }

  get ownerUsername(): string | null {
    return this.file.ownerUsername;
  }

  get originalImageId(): string | null | undefined {
    return this.file.originalImageId;
  }

  get path(): ReadonlyArray<GalleryFile> {
    return this.file.path;
  }

  pathAsString(): string {
    return this.file.pathAsString();
  }

  get isFolder(): boolean {
    return this.file.isFolder;
  }

  get isSystemFolder(): boolean {
    return this.file.isSystemFolder;
  }

  get isSharedFolder(): boolean {
    return this.file.isSharedFolder;
  }

  get isImage(): boolean {
    return this.file.isImage;
  }

  get isSnippet(): boolean {
    return this.file.isSnippet;
  }

  get isSnippetFolder(): boolean {
    return this.file.isSnippetFolder;
  }

  /* Applied to this version's name, not the live one, to match `name`. */
  transformFilename(f: (filename: string) => string): string {
    if (this.versionName === null) return this.file.transformFilename(f);
    if (!this.versionName.includes(".")) return f(this.versionName);
    return `${f(filenameExceptExtension(this.versionName))}.${justFilenameExtension(this.versionName)}`;
  }

  get linkedDocuments(): React.ReactNode {
    return this.file.linkedDocuments;
  }

  get metadata(): Record<string, string> {
    return this.file.metadata;
  }

  get treeViewItemId(): string {
    return this.file.treeViewItemId;
  }

  get canViewVersionHistory(): Result<null> {
    return this.file.canViewVersionHistory;
  }

  get canOpen(): Result<null> {
    return locked();
  }

  get canDuplicate(): Result<null> {
    return locked();
  }

  get canDelete(): Result<null> {
    return locked();
  }

  get canRename(): Result<null> {
    return locked();
  }

  get canMoveToIrods(): Result<null> {
    return locked();
  }

  get canMoveToS3(): Result<null> {
    return locked();
  }

  /*
   * Export is refused rather than delegated because it is not version-aware: it
   * would export the live bytes while the panel says version N. Share needs no
   * handling here, being offered for snippets only, which are not media files
   * and so have no version to pin in the first place.
   */
  get canBeExported(): Result<null> {
    return locked();
  }

  get canBeMoved(): Result<null> {
    return locked();
  }

  get canUploadNewVersion(): Result<null> {
    return locked();
  }

  get canBeEdited(): Result<null> {
    return locked();
  }

  get canBeLoggedOutOf(): Result<null> {
    return locked();
  }
}
