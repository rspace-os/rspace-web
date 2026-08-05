package com.researchspace.service.archive.export;

import com.researchspace.model.ArchivalCheckSum;
import java.util.List;

/**
 * Defines policy for deciding when an HTML or XML export ( or any other resource on the server,
 * actually) becomes eligible for deletion.
 */
public interface ExportRemovalPolicy {

  /**
   * Boolean test as to whether an exported archive should be removed or not
   *
   * @param archive An {@link ArchivalCheckSum}.
   * @return <code>true</code> if the archive represented by the supplied {@link ArchivalCheckSum}
   *     should be deleted, <code>false</code> otherwise.
   */
  boolean removeExport(ArchivalCheckSum archive);

  /**
   * Gets a human-readable message describing when the archive will be eligible for deletion. When
   * an archive become eligible for deletion, the exact time of it's removal may vary, depending on
   * how often the archives are scanned by the deletion mechanism.
   *
   * @return the untranslated message key and arguments
   */
  RemovalCircumstancesMessage getRemovalCircumstancesMessage();

  record RemovalCircumstancesMessage(String key, List<Object> arguments) {}

  /** Always returns false - the archive should not be removed. */
  ExportRemovalPolicy FALSE =
      new ExportRemovalPolicy() {
        @Override
        public boolean removeExport(ArchivalCheckSum archive) {
          return false;
        }

        @Override
        public RemovalCircumstancesMessage getRemovalCircumstancesMessage() {
          return new RemovalCircumstancesMessage(
              "email.notification.exportCompleteNotification.removalPolicyNever", List.of());
        }
      };

  /** Always returns true - the archive should be removed. */
  ExportRemovalPolicy TRUE =
      new ExportRemovalPolicy() {
        @Override
        public boolean removeExport(ArchivalCheckSum archive) {
          return true;
        }

        @Override
        public RemovalCircumstancesMessage getRemovalCircumstancesMessage() {
          return new RemovalCircumstancesMessage(
              "email.notification.exportCompleteNotification.removalPolicyNow", List.of());
        }
      };
}
