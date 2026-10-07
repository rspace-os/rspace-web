import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows/sessions/cloudSessions";
import { alphaNumericUnique, DYNAMIC_USER_PASSWORD, E2E_AFFILIATION, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Community group invitations", () => {
  test("As a group creator, I can invite someone without an account; they sign up from the invite and join", async ({
    flowCloudUser,
    flowCloudVisitor,
    clientMailpit,
  }) => {
    const pi = await flowCloudUser("e2eCloudPi");
    const groupName = uniqueName("e2eCloudGroup");
    const inviteeUsername = alphaNumericUnique("e2eCloudInvitee");
    const inviteeEmail = `${inviteeUsername}@example.com`;

    await test.step("Given I create a group inviting a new email address", async () => {
      await pi.createGroup.open();
      await pi.createGroup.createGroup({ name: groupName, inviteNewEmails: [inviteeEmail] });
      await expect(pi.createGroup.resultAlert(groupName)).toHaveText(`${groupName} group created successfully!`);
    });

    const invitee = await flowCloudVisitor();
    const inviteLink = await test.step("read the invite link from the email", () =>
      clientMailpit.waitForLink(inviteeEmail, "Join Group Invitation - Research Space", "/signup?token="));

    await test.step("When the invitee signs up from the emailed link", async () => {
      await invitee.signup.openWithInviteLink(inviteLink);
      await expect(invitee.signup.emailInput).toHaveValue(inviteeEmail);
      await invitee.signup.signUp({
        username: inviteeUsername,
        password: DYNAMIC_USER_PASSWORD,
        firstName: "E2E",
        lastName: "CloudInvitee",
        affiliation: E2E_AFFILIATION,
        acceptTerms: true,
      });
      // The invite already proves the address, so no activation email is needed.
      await expect(invitee.verification.heading("Completed!")).toBeVisible();
    });

    await test.step("And accepts the group invitation after logging in", async () => {
      await invitee.login.open();
      await invitee.login.login(inviteeUsername, DYNAMIC_USER_PASSWORD);
      await invitee.groupRequest(groupName).accept();
    });

    await test.step("Then the invitee is a member of the group", async () => {
      await pi.profile.open();
      await pi.profile.groupLink(groupName).click();
      await expect(pi.groupDetails.memberRole(inviteeUsername)).toHaveText("User");
    });
  });

  test("As a group creator, I can nominate an existing user as PI; when they accept they become PI, I become Lab Admin, and my invitee can join", async ({
    flowCloudUser,
  }) => {
    const creator = await flowCloudUser("e2eCloudCreator");
    const nominatedPi = await flowCloudUser("e2eCloudNominee");
    const member = await flowCloudUser("e2eCloudMember");
    const groupName = uniqueName("e2eCloudNominated");

    await test.step("Given I request a group with another user as PI and an existing user as member", async () => {
      await creator.createGroup.open();
      await creator.createGroup.createGroup({
        name: groupName,
        nominatedPiEmail: nominatedPi.email,
        inviteExistingEmails: [member.email],
      });
      await expect(creator.createGroup.resultAlert(groupName)).toHaveText(
        `Group creation request for ${groupName} sent successfully`,
      );
    });

    await test.step("When the nominated PI accepts", async () => {
      await nominatedPi.page.reload();
      await nominatedPi.groupRequest(groupName).accept();
    });

    await test.step("And the invited member accepts the invitation that follows", async () => {
      await member.page.reload();
      await member.groupRequest(groupName).accept();
    });

    await test.step("Then the group has the nominee as PI, me as Lab Admin and the member as User", async () => {
      await nominatedPi.profile.open();
      await nominatedPi.profile.groupLink(groupName).click();
      await expect(nominatedPi.groupDetails.memberRole(nominatedPi.username)).toHaveText("PI");
      await expect(nominatedPi.groupDetails.memberRole(creator.username)).toHaveText("Lab Admin");
      await expect(nominatedPi.groupDetails.memberRole(member.username)).toHaveText("User");
    });
  });

  test("As a group creator, when my nominated PI declines, no group is created and I am told it was rejected", async ({
    flowCloudUser,
  }) => {
    const creator = await flowCloudUser("e2eCloudCreator");
    const nominatedPi = await flowCloudUser("e2eCloudDecliner");
    const groupName = uniqueName("e2eCloudDeclined");

    await test.step("Given I request a group with another user as PI", async () => {
      await creator.createGroup.open();
      await creator.createGroup.createGroup({ name: groupName, nominatedPiEmail: nominatedPi.email });
      await expect(creator.createGroup.resultAlert(groupName)).toHaveText(
        `Group creation request for ${groupName} sent successfully`,
      );
    });

    await test.step("When the nominated PI declines", async () => {
      await nominatedPi.page.reload();
      await nominatedPi.groupRequest(groupName).decline();
    });

    await test.step("Then I am notified that the request was rejected", async () => {
      await creator.workspace.open();
      await creator.notifications.open();
      await expect(
        creator.notifications.notification(
          `${nominatedPi.fullName} updated request status, altered from NEW to REJECTED`,
        ),
      ).toBeVisible();
      await creator.notifications.close();
    });

    await test.step("And neither of us belongs to the group", async () => {
      await creator.profile.open();
      await expect(creator.profile.groupLink(groupName)).toHaveCount(0);
      await nominatedPi.profile.open();
      await expect(nominatedPi.profile.groupLink(groupName)).toHaveCount(0);
    });
  });
});
