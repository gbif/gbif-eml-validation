package org.gbif.eml.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the metadata document of an unpacked Darwin Core Archive, formerly {@code MetadataPath}
 * in pipelines' validator-eml.
 */
public final class ArchiveMetadata {

  private static final String DEFAULT_NAME = "eml.xml";
  private static final String ALTERNATIVE_NAME = "metadata.xml";
  private static final Pattern METADATA_ATTRIBUTE =
      Pattern.compile("metadata=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

  private ArchiveMetadata() {}

  /**
   * The file named by the {@code metadata} attribute of {@code meta.xml}; otherwise {@code
   * eml.xml} or {@code metadata.xml}, the two defaults of the dwca-io library. Empty when none
   * exists.
   *
   * <p>The {@code meta.xml} location is returned as declared, even if that file does not exist,
   * so the caller can report the broken reference. A declared location outside the archive is
   * ignored.
   */
  public static Optional<Path> find(Path archiveDirectory) throws IOException {
    Path metaXml = archiveDirectory.resolve("meta.xml");
    if (Files.exists(metaXml)) {
      Matcher matcher = METADATA_ATTRIBUTE.matcher(Files.readString(metaXml));
      if (matcher.find()) {
        Path declared = archiveDirectory.resolve(matcher.group(1)).normalize();
        // A reference outside the archive (for example "../x" or a URL) is ignored.
        if (declared.startsWith(archiveDirectory.normalize())) {
          return Optional.of(declared);
        }
      }
    }
    for (String name : new String[] {DEFAULT_NAME, ALTERNATIVE_NAME}) {
      Path candidate = archiveDirectory.resolve(name);
      if (Files.exists(candidate)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }
}
