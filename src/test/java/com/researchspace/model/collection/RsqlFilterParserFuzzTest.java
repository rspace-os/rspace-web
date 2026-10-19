package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RsqlFilterParserFuzzTest {

  private static final long FUZZ_SEED = 0x5253514cL;
  private static final int FUZZ_CASES = 10_000;
  private static final String FUZZ_CHARACTERS =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
          + "=!<>(),;'\"\\*._- \t\r\n\u0000\u00a0\u2028\ud83d\ude00";
  private static final List<String> VALID_SEEDS =
      List.of(
          "id==1",
          "name=='Ada Lovelace'",
          "name=contains='a(b)'",
          "name=like='a b'",
          "name=exists=true",
          "id=in=(1,2,3)",
          "id=out=(1,2,3)",
          "(id>1;id<=5),name!=*x*");
  private static final CollectionDescription<Item> DESCRIPTION =
      new CollectionDescription<>(
          "items",
          Item.class,
          List.of(
              Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Item::id),
              Field.readOnly("name", "name", CollectionFieldTypes.text(100), Item::name)),
          "id",
          List.of(new Sort("id", true)));

  @Test
  void mutatedAndArbitraryInputsOnlyReturnFiltersOrQueryErrors() {
    SplittableRandom random = new SplittableRandom(FUZZ_SEED);
    RsqlFilterParser parser = new RsqlFilterParser(DESCRIPTION);

    for (int caseNumber = 0; caseNumber < FUZZ_CASES; caseNumber++) {
      String input =
          caseNumber % 2 == 0
              ? mutate(VALID_SEEDS.get(random.nextInt(VALID_SEEDS.size())), random)
              : arbitraryInput(random);
      try {
        parser.parse(input);
      } catch (CollectionQueryException expected) {
        // Invalid caller input must stay inside the public query-error contract.
      } catch (RuntimeException unexpected) {
        fail("Fuzz case " + caseNumber + " escaped for input " + escaped(input), unexpected);
      }
    }
  }

  @Test
  void dependencyOperatorsOutsideTheApplicationModelAreNotRegistered() {
    RsqlFilterParser parser = new RsqlFilterParser(DESCRIPTION);

    for (String input : List.of("name=null=", "name=notnull=")) {
      CollectionQueryException error =
          assertThrows(CollectionQueryException.class, () -> parser.parse(input), input);

      assertEquals(CollectionQueryException.Reason.SYNTAX, error.getReason(), input);
    }
  }

  @Test
  void rejectsOversizedInputBeforeParsing() {
    RsqlFilterParser parser = new RsqlFilterParser(DESCRIPTION);

    for (String character : List.of("x", " ")) {
      String input = character.repeat(CollectionQueryLimits.MAX_WHERE_LENGTH + 1);
      CollectionQueryException error =
          assertThrows(CollectionQueryException.class, () -> parser.parse(input));

      assertEquals(CollectionQueryException.Reason.COMPLEXITY, error.getReason());
    }
  }

  private static String mutate(String seed, SplittableRandom random) {
    StringBuilder value = new StringBuilder(seed);
    int edits = 1 + random.nextInt(8);
    for (int edit = 0; edit < edits; edit++) {
      int index = random.nextInt(value.length() + 1);
      switch (random.nextInt(3)) {
        case 0 -> value.insert(index, randomCharacter(random));
        case 1 -> {
          if (!value.isEmpty()) {
            value.setCharAt(Math.min(index, value.length() - 1), randomCharacter(random));
          }
        }
        case 2 -> {
          if (!value.isEmpty()) {
            value.deleteCharAt(Math.min(index, value.length() - 1));
          }
        }
        default -> throw new IllegalStateException("Unexpected mutation");
      }
    }
    return value.toString();
  }

  private static String arbitraryInput(SplittableRandom random) {
    int length = random.nextInt(129);
    StringBuilder value = new StringBuilder(length);
    for (int index = 0; index < length; index++) {
      value.append(randomCharacter(random));
    }
    return value.toString();
  }

  private static char randomCharacter(SplittableRandom random) {
    return FUZZ_CHARACTERS.charAt(random.nextInt(FUZZ_CHARACTERS.length()));
  }

  private static String escaped(String value) {
    return value
        .chars()
        .mapToObj(character -> "\\u%04x".formatted(character))
        .collect(Collectors.joining());
  }

  private record Item(Long id, String name) {}
}
