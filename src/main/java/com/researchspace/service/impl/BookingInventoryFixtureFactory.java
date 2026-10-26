package com.researchspace.service.impl;

import static com.researchspace.model.inventory.Container.ContainerType.LIST;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

class BookingInventoryFixtureFactory {

  private final ContainerDao containerDao;
  private final InstrumentDao instrumentDao;
  private final ContainerApiManager containerManager;
  private final InstrumentEntityApiManager instrumentManager;
  private final MessageSourceUtils messages;
  private static final String FIXTURE_DESCRIPTION_KEY = "bookingFixtures.description";
  private static final Locale FIXTURE_LOCALE = Locale.forLanguageTag("en-US");

  BookingInventoryFixtureFactory(
      ContainerDao containerDao,
      InstrumentDao instrumentDao,
      ContainerApiManager containerManager,
      InstrumentEntityApiManager instrumentManager,
      MessageSourceUtils messages) {
    this.containerDao = containerDao;
    this.instrumentDao = instrumentDao;
    this.containerManager = containerManager;
    this.instrumentManager = instrumentManager;
    this.messages = messages;
  }

  Instrument ensureInstrument(String name, User owner) {
    String fixtureDescription = message(FIXTURE_DESCRIPTION_KEY);
    return instrumentDao.findInstrumentsByName(name, owner).stream()
        .filter(instrument -> !instrument.isDeleted())
        .filter(instrument -> fixtureDescription.equals(instrument.getDescription()))
        .findFirst()
        .orElseGet(() -> createInstrument(name, fixtureDescription, owner));
  }

  private Instrument createInstrument(String name, String description, User owner) {
    ApiInstrument request = new ApiInstrument();
    request.setName(name);
    request.setDescription(description);
    ApiInstrument created = instrumentManager.createNewApiInstrument(request, owner);
    return instrumentDao.get(created.getId());
  }

  Container ensureContainer(String name, User owner, boolean includeDeleted) {
    String fixtureDescription = message(FIXTURE_DESCRIPTION_KEY);
    return containerDao.getAll().stream()
        .filter(container -> name.equals(container.getName()))
        .filter(container -> owner.getId().equals(container.getOwner().getId()))
        .filter(container -> fixtureDescription.equals(container.getDescription()))
        .filter(container -> includeDeleted || !container.isDeleted())
        .findFirst()
        .orElseGet(
            () -> {
              ApiContainer request = new ApiContainer(name, LIST);
              request.setDescription(fixtureDescription);
              ApiContainer created = containerManager.createNewApiContainer(request, owner);
              return containerDao.get(created.getId());
            });
  }

  String message(String key) {
    return messages.getMessageForLocale(key, FIXTURE_LOCALE);
  }

  void moveToParent(Instrument instrument, Container parent) {
    if (!hasParent(instrument, parent)) {
      instrument.moveToNewParent(parent);
      instrumentDao.save(instrument);
    }
  }

  boolean hasParent(Instrument instrument, Container parent) {
    return instrument.getParentContainer() != null
        && parent.getId().equals(instrument.getParentContainer().getId());
  }

  void removeFromParent(Instrument instrument) {
    if (instrument.getParentContainer() != null) {
      instrument.removeFromCurrentParent();
      instrumentDao.save(instrument);
    }
  }

  List<Instrument> ensureBusyCalendarInstruments(User owner, int busyCalendarInstrumentCount) {
    String fixtureDescription = message(FIXTURE_DESCRIPTION_KEY);
    Map<String, Instrument> existingByName =
        instrumentDao.getAll().stream()
            .filter(instrument -> !instrument.isDeleted())
            .filter(instrument -> owner.getId().equals(instrument.getOwner().getId()))
            .filter(instrument -> fixtureDescription.equals(instrument.getDescription()))
            .collect(
                Collectors.toMap(
                    Instrument::getName, instrument -> instrument, (first, duplicate) -> first));
    List<Instrument> instruments = new ArrayList<>(busyCalendarInstrumentCount);
    for (int index = 1; index <= busyCalendarInstrumentCount; index++) {
      String name =
          messages.getMessage(
              "bookingFixtures.instruments.busyCalendar", new Object[] {index}, FIXTURE_LOCALE);
      instruments.add(
          existingByName.computeIfAbsent(
              name, ignored -> createInstrument(name, fixtureDescription, owner)));
    }
    return instruments;
  }
}
