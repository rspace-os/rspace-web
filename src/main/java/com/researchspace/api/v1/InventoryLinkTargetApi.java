/**
 * RSpace Inventory API: read-time summary of an Inventory Link target. Backs the "Target deleted"
 * pill and target-name display on link cards without populating a summary into every outgoing link
 * payload (which would run on list endpoints; see RSDEV-1182).
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiInventoryLinkTargetSummary;
import com.researchspace.model.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/api/inventory/v1")
public interface InventoryLinkTargetApi {

  /**
   * Resolves the current state (globalId, name, type, deleted) of a link target, Inventory or ELN
   * alike. Permission-redacted: targets the caller cannot read return a globalId-only summary.
   */
  @GetMapping("/linkTargets/{globalId}/summary")
  ApiInventoryLinkTargetSummary getLinkTargetSummary(@PathVariable String globalId, User user);

  /**
   * The same summary for a link target that is a file or folder inside a filestore. Readable only
   * to the filestore's owner; anyone else, and an unknown filestore, get the same redacted summary.
   */
  @GetMapping("/linkTargets/filestores/{filestoreId}/summary")
  ApiInventoryLinkTargetSummary getFilestoreLinkTargetSummary(
      @PathVariable Long filestoreId, @RequestParam("remotePath") String remotePath, User user);
}
