package com.researchspace.service.impl;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import com.researchspace.Constants;
import com.researchspace.dao.DatabaseUsageByGroupGroupByResult;
import com.researchspace.dao.FileMetadataDao;
import com.researchspace.dao.GroupDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.field.LocalizedIllegalArgumentException;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.CommunicationNotifyPolicy;
import com.researchspace.service.LabGroupStorageAlertManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.NotificationConfig;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.UserLocaleService;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service("labGroupStorageAlertManager")
public class LabGroupStorageAlertManagerImpl implements LabGroupStorageAlertManager {

  private static final SystemPropertyName THRESHOLD =
      SystemPropertyName.LABGROUP_STORAGE_ALERT_THRESHOLD_MB;
  private static final Pattern WHOLE_MB = Pattern.compile("[1-9]\\d{0,11}");
  private static final double BYTES_PER_MB = 1_000_000d;

  private final SystemPropertyManager systemPropertyManager;
  private final FileMetadataDao fileMetadataDao;
  private final GroupDao groupDao;
  private final UserDao userDao;
  private final CommunicationManager communicationManager;
  private final MessageSourceUtils messages;
  private final UserLocaleService userLocaleService;
  private final Clock clock;

  @Autowired
  public LabGroupStorageAlertManagerImpl(
      SystemPropertyManager systemPropertyManager,
      FileMetadataDao fileMetadataDao,
      GroupDao groupDao,
      UserDao userDao,
      CommunicationManager communicationManager,
      MessageSourceUtils messages,
      UserLocaleService userLocaleService) {
    this(
        systemPropertyManager,
        fileMetadataDao,
        groupDao,
        userDao,
        communicationManager,
        messages,
        userLocaleService,
        Clock.systemUTC());
  }

  LabGroupStorageAlertManagerImpl(
      SystemPropertyManager systemPropertyManager,
      FileMetadataDao fileMetadataDao,
      GroupDao groupDao,
      UserDao userDao,
      CommunicationManager communicationManager,
      MessageSourceUtils messages,
      UserLocaleService userLocaleService,
      Clock clock) {
    this.systemPropertyManager = systemPropertyManager;
    this.fileMetadataDao = fileMetadataDao;
    this.groupDao = groupDao;
    this.userDao = userDao;
    this.communicationManager = communicationManager;
    this.messages = messages;
    this.userLocaleService = userLocaleService;
    this.clock = clock;
  }

  @Override
  public Optional<Long> getThresholdMb() {
    SystemPropertyValue stored = systemPropertyManager.findByName(THRESHOLD);
    String value = stored == null ? null : stored.getValue();
    if (StringUtils.isBlank(value)) {
      return Optional.empty();
    }
    if (!WHOLE_MB.matcher(value).matches()) {
      log.warn("Ignoring unreadable {} value [{}]", THRESHOLD.getPropertyName(), value);
      return Optional.empty();
    }
    return Optional.of(Long.valueOf(value));
  }

  @Override
  public Optional<Long> setThresholdMb(String rawThresholdMb, User subject) {
    String value = StringUtils.trimToEmpty(rawThresholdMb);
    if (!value.isEmpty() && !WHOLE_MB.matcher(value).matches()) {
      throw new LocalizedIllegalArgumentException(
          "system.groupList.storageAlert.errors.invalidThreshold");
    }
    Optional<Long> thresholdMb =
        value.isEmpty() ? Optional.empty() : Optional.of(Long.valueOf(value));
    evaluate(thresholdMb);
    // last: save updates a cache that a rollback of the evaluation would not undo
    systemPropertyManager.save(THRESHOLD, value, subject);
    return thresholdMb;
  }

  @Override
  public void checkLabGroups() {
    evaluate(getThresholdMb());
  }

  // ponytail: no lock; a threshold save racing the scheduled run could alert a group twice.
  // Add a lock if that is ever seen.
  private void evaluate(Optional<Long> thresholdMb) {
    Set<Long> reported = new HashSet<>(groupDao.getStorageAlertSentGroupIds());
    Map<Long, Double> atOrAbove = new HashMap<>();
    thresholdMb.ifPresent(
        mb -> {
          for (DatabaseUsageByGroupGroupByResult row :
              fileMetadataDao.getTotalFileUsageForAllLabGroups()) {
            if (row.getFileusage() >= mb * BYTES_PER_MB) {
              atOrAbove.put(row.getGroupId(), row.getFileusage());
            }
          }
        });

    Set<Long> toReArm = new HashSet<>(reported);
    toReArm.removeAll(atOrAbove.keySet());
    if (!toReArm.isEmpty()) {
      groupDao.setStorageAlertSent(toReArm, false);
    }

    Set<Long> toReport = new HashSet<>(atOrAbove.keySet());
    toReport.removeAll(reported);
    if (toReport.isEmpty()) {
      return;
    }
    PaginationCriteria<User> allUsers = PaginationCriteria.createDefaultForClass(User.class);
    allUsers.setGetAllResults();
    Set<User> sysadmins =
        userDao.listUsersByRole(Role.SYSTEM_ROLE, allUsers).getResults().stream()
            .filter(User::isEnabled)
            .collect(toSet());
    if (sysadmins.isEmpty()) {
      log.warn("No enabled sysadmin to alert about the storage of Lab Groups {}", toReport);
      return;
    }

    User originator = userDao.getUserByUsername(Constants.SYSADMIN_UNAME);
    Locale locale = userLocaleService.getLocale();
    NumberFormat mbFormat = NumberFormat.getNumberInstance(locale);
    mbFormat.setMaximumFractionDigits(1);
    String threshold = mbFormat.format(thresholdMb.orElseThrow());
    String checkedOn =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", locale)
            .withZone(ZoneOffset.UTC)
            .format(clock.instant());
    for (Group group : groupDao.getGroups(toReport)) {
      String pis =
          group.getPiusers().stream()
              .map(User::getFullName)
              .sorted(String.CASE_INSENSITIVE_ORDER)
              .map(StringEscapeUtils::escapeHtml4)
              .collect(joining(", "));
      Object[] args = {
        StringEscapeUtils.escapeHtml4(group.getDisplayName()),
        pis,
        mbFormat.format(atOrAbove.get(group.getId()) / BYTES_PER_MB),
        threshold,
        checkedOn
      };
      NotificationConfig config =
          NotificationConfig.builder()
              .notificationType(NotificationType.LAB_GROUP_STORAGE_ALERT)
              .broadcast(true)
              .policyOverride(CommunicationNotifyPolicy.ALWAYS_NOTIFY)
              .recordAuthorisationRequired(false)
              .notificationEventPreferenceOverride(true)
              .includeOriginator(true)
              .notificationTargetsOverride(new HashSet<>(sysadmins))
              .build();
      communicationManager.notify(
          originator,
          null,
          config,
          messages.getMessage("system.groupList.storageAlert.notification", args, locale));
    }
    groupDao.setStorageAlertSent(toReport, true);
  }
}
