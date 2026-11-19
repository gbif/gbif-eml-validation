package org.gbif.eml.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveMetadataTest {

  @TempDir Path archive;

  @Test
  void metaXmlLocationWins() throws IOException {
    meta("<archive xmlns=\"http://rs.tdwg.org/dwc/text/\" metadata='docs/dataset.xml'>");
    Files.writeString(archive.resolve("eml.xml"), "<eml/>");

    assertEquals(Optional.of(archive.resolve("docs/dataset.xml")), ArchiveMetadata.find(archive));
  }

  @Test
  void fallsBackToEmlXmlThenMetadataXml() throws IOException {
    meta("<archive xmlns=\"http://rs.tdwg.org/dwc/text/\">");
    Files.writeString(archive.resolve("metadata.xml"), "<eml/>");
    assertEquals(Optional.of(archive.resolve("metadata.xml")), ArchiveMetadata.find(archive));

    Files.writeString(archive.resolve("eml.xml"), "<eml/>");
    assertEquals(Optional.of(archive.resolve("eml.xml")), ArchiveMetadata.find(archive));
  }

  @Test
  void locationOutsideTheArchiveIsIgnored() throws IOException {
    meta("<archive metadata=\"../../etc/passwd\">");

    assertTrue(ArchiveMetadata.find(archive).isEmpty());
  }

  @Test
  void emptyWhenNothingExists() throws IOException {
    assertTrue(ArchiveMetadata.find(archive).isEmpty());
  }

  private void meta(String root) throws IOException {
    Files.writeString(archive.resolve("meta.xml"), root + "</archive>");
  }
}
