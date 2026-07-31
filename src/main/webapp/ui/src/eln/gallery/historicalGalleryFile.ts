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
 * A decorator rather than a read-only flag on the live file, so the object
 * cannot report a version it is not showing and nothing downstream has to
 * remember to check a flag. See CONTEXT.md, "Version", for which fields belong
 * to a version rather than to the item.
 *
 * Two members are delegated on purpose: `globalId`, which stays unversioned
 * because the reference lookups that read it do not record versions (ADR 0004),
 * and `canViewVersionHistory`, so the view is not a dead end.
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

  /*
   * Constant refusals, so values rather than getters. Export is refused rather
   * than delegated because it is not version-aware: it would export the live
   * bytes while the panel says version N. Share needs no handling here, being
   * offered for snippets only, which have no version to pin.
   */
  readonly canOpen = locked<null>();
  readonly canDuplicate = locked<null>();
  readonly canDelete = locked<null>();
  readonly canRename = locked<null>();
  readonly canMoveToIrods = locked<null>();
  readonly canMoveToS3 = locked<null>();
  readonly canBeExported = locked<null>();
  readonly canBeMoved = locked<null>();
  readonly canUploadNewVersion = locked<null>();
  readonly canBeEdited = locked<null>();
  readonly canBeLoggedOutOf = locked<null>();
}
