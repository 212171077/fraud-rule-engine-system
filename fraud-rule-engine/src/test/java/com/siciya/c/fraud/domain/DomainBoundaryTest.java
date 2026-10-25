package com.siciya.c.fraud.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Keeps domain and port code free of framework and infrastructure dependencies. */
class DomainBoundaryTest {

  private static final Path ROOT = Path.of("src/main/java/com/siciya/c/fraud");
  private static final List<String> FORBIDDEN_IMPORT_PREFIXES =
      List.of(
          "import org.springframework.",
          "import jakarta.",
          "import javax.persistence.",
          "import org.hibernate.",
          "import org.apache.kafka.",
          "import com.fasterxml.",
          "import io.micrometer.",
          "import java.sql.",
          "import java.net.http.");

  @Test
  void domainPackagesDoNotDependOnFrameworksOrInfrastructure() throws IOException {
    assertThat(violations(List.of(ROOT.resolve("domain")), FORBIDDEN_IMPORT_PREFIXES)).isEmpty();
  }

  @Test
  void applicationPortsDoNotDependOnFrameworksOrInfrastructure() throws IOException {
    assertThat(violations(List.of(ROOT.resolve("application/port")), FORBIDDEN_IMPORT_PREFIXES))
        .isEmpty();
  }

  @Test
  void domainAndPortsDoNotDependOnAdaptersOrConfiguration() throws IOException {
    assertThat(
            violations(
                List.of(ROOT.resolve("domain"), ROOT.resolve("application/port")),
                List.of(
                    "import com.siciya.c.fraud.adapter.",
                    "import com.siciya.c.fraud.configuration.")))
        .isEmpty();
  }

  @Test
  void domainDoesNotReadTheSystemClock() throws IOException {
    List<String> clockCalls =
        List.of(
            "Instant.now(",
            "LocalDateTime.now(",
            "ZonedDateTime.now(",
            "OffsetDateTime.now(",
            "System.currentTimeMillis(");
    List<String> clockReads = new ArrayList<>();
    for (Path file : javaFiles(ROOT.resolve("domain"))) {
      String source = Files.readString(file);
      for (String call : clockCalls) {
        if (source.contains(call)) {
          clockReads.add(ROOT.relativize(file) + " uses " + call);
        }
      }
    }
    assertThat(clockReads).isEmpty();
  }

  private static List<String> violations(List<Path> directories, List<String> prefixes)
      throws IOException {
    List<String> violations = new ArrayList<>();
    for (Path directory : directories) {
      for (Path file : javaFiles(directory)) {
        for (String line : Files.readAllLines(file)) {
          String trimmed = line.strip();
          for (String prefix : prefixes) {
            if (trimmed.startsWith(prefix)) {
              violations.add(ROOT.relativize(file) + ": " + trimmed);
            }
          }
        }
      }
    }
    return violations;
  }

  private static List<Path> javaFiles(Path directory) throws IOException {
    try (Stream<Path> files = Files.walk(directory)) {
      return files.filter(path -> path.toString().endsWith(".java")).toList();
    }
  }
}
