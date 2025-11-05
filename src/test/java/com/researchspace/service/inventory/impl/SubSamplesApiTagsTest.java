package com.researchspace.service.inventory.impl;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.service.DocumentTagManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure unit tests for tag-driven ontology updates for SubSamples.
 *
 * <p>These tests verify that InventoryApiManagerImpl triggers DocumentTagManager updates when tags
 * are present or changed. By keeping this logic in a dedicated unit test, we avoid mutating Spring
 * singletons in integration tests.
 */
@ExtendWith(MockitoExtension.class)
public class SubSamplesApiTagsTest {

  @Mock private DocumentTagManager documentTagManager;
  @Mock private User user;
  @InjectMocks private SubSampleApiManagerImpl subSampleApiManager;

  @Test
  public void updateOntologyOnRecordChanges_invokedOnlyWhenTagsPresent() {
    ApiSubSample withoutTags = new ApiSubSample();
    withoutTags.setApiTagInfo("");

    ApiSubSample withTags = new ApiSubSample();
    withTags.setApiTagInfo("tag1, tag2");

    subSampleApiManager.updateOntologyOnRecordChanges(withoutTags, user);
    verify(documentTagManager, never()).updateUserOntologyDocument(eq(user));

    subSampleApiManager.updateOntologyOnRecordChanges(withTags, user);
    verify(documentTagManager, times(1)).updateUserOntologyDocument(eq(user));
  }

  @Test
  public void updateOntologyOnUpdate_invokedOnlyWhenTagsChanged() {
    ApiSubSample original = new ApiSubSample();
    original.setApiTagInfo("");

    ApiSubSample updatedNoChange = new ApiSubSample();
    updatedNoChange.setApiTagInfo("");

    ApiSubSample updatedWithTags = new ApiSubSample();
    updatedWithTags.setApiTagInfo("x,y");

    // No change
    subSampleApiManager.updateOntologyOnUpdate(original, updatedNoChange, user);
    verify(documentTagManager, never()).updateUserOntologyDocument(eq(user));

    // Change detected
    subSampleApiManager.updateOntologyOnUpdate(original, updatedWithTags, user);
    verify(documentTagManager, times(1)).updateUserOntologyDocument(eq(user));
  }

  @Test
  public void delete_invokesOntologyUpdateWhenTagged() {
    // Deletion path calls updateOntologyOnRecordChanges on the resulting ApiSubSample
    ApiSubSample deletedView = new ApiSubSample();
    deletedView.setApiTagInfo("ontology:TAG");

    subSampleApiManager.updateOntologyOnRecordChanges(deletedView, user);
    verify(documentTagManager, times(1)).updateUserOntologyDocument(eq(user));
  }

  @Test
  public void restore_invokesOntologyUpdateWhenTagged() {
    // Restore path calls updateOntologyOnRecordChanges on the resulting ApiSubSample
    ApiSubSample restoredView = new ApiSubSample();
    restoredView.setApiTagInfo("ontology:RESTORED");

    subSampleApiManager.updateOntologyOnRecordChanges(restoredView, user);
    verify(documentTagManager, times(1)).updateUserOntologyDocument(eq(user));
  }
}
