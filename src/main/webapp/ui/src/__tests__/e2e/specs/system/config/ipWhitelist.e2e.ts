import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

const INVALID_IP_MESSAGE = "Please enter a valid IPv4, CIDR, or IPv6 address";
const MISSING_DESCRIPTION_MESSAGE = "Please enter a human-readable identifier for this IP address";

test.describe("Sysadmin IP white list", { tag: tags.SYSTEM }, () => {
  test("As a sysadmin, I can white-list an IP address and a CIDR range, rename one, and remove it", async ({
    flowIpWhitelist,
  }) => {
    const { whitelist, uniqueIpAddress } = flowIpWhitelist;
    const ipAddress = uniqueIpAddress("address");
    const cidrRange = uniqueIpAddress("range");
    const description = uniqueName("e2eIp");
    const renamed = uniqueName("e2eIpRenamed");

    await whitelist.add(ipAddress, description);
    await whitelist.add(cidrRange, uniqueName("e2eCidr"));
    await expect(whitelist.descriptionCell(ipAddress)).toHaveText(description);
    await expect(whitelist.row(cidrRange)).toBeVisible();

    await whitelist.editDescription(ipAddress, renamed);
    await expect(whitelist.descriptionCell(ipAddress)).toHaveText(renamed);

    await whitelist.remove(ipAddress);
    await expect(whitelist.row(ipAddress)).toHaveCount(0);
    await expect(whitelist.row(cidrRange)).toBeVisible();
  });

  test("As a sysadmin, I'm warned and nothing is saved when the IP address is missing or has invalid characters, or the description is missing", async ({
    flowIpWhitelist,
  }) => {
    const { whitelist, uniqueIpAddress } = flowIpWhitelist;
    const ipAddress = uniqueIpAddress("address");

    for (const malformed of ["", "@£$$$"]) {
      const alert = await whitelist.addExpectingError(malformed, uniqueName("e2eIp"));
      await expect(alert.message).toHaveText(INVALID_IP_MESSAGE);
      await alert.confirm();
    }

    const alert = await whitelist.addExpectingError(ipAddress, "");
    await expect(alert.message).toHaveText(MISSING_DESCRIPTION_MESSAGE);
    await alert.confirm();
    await expect(whitelist.row(ipAddress)).toHaveCount(0);
  });
});
