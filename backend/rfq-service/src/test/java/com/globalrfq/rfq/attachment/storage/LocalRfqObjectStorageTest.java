package com.globalrfq.rfq.attachment.storage;

import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.config.RfqFileProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalRfqObjectStorageTest {
    @TempDir
    Path storageDirectory;

    private LocalRfqObjectStorage storage;

    @BeforeEach
    void setUp() {
        RfqFileProperties properties = new RfqFileProperties();
        properties.setStorageDirectory(storageDirectory);
        storage = new LocalRfqObjectStorage(properties);
    }

    @Test
    void storesReadsAndDeletesAttachments() throws Exception {
        String key = "f1cc8e2e-7c2c-4bca-aabc-41c2890f9058";
        byte[] content = "private RFQ attachment".getBytes(StandardCharsets.UTF_8);
        Path source = Files.createTempFile(storageDirectory, "upload-", ".bin");
        Files.write(source, content);

        storage.put(RfqObjectStorage.ObjectKind.ATTACHMENT, key, source);
        try (InputStream stored = storage.open(RfqObjectStorage.ObjectKind.ATTACHMENT, key)) {
            assertArrayEquals(content, stored.readAllBytes());
        }

        storage.delete(RfqObjectStorage.ObjectKind.ATTACHMENT, key);
        assertThrows(RfqException.class, () -> storage.open(RfqObjectStorage.ObjectKind.ATTACHMENT, key));
    }

    @Test
    void rejectsPathTraversalKeys() {
        assertThrows(RfqException.class,
                () -> storage.open(RfqObjectStorage.ObjectKind.ATTACHMENT, "../../outside"));
    }

    @Test
    void storesImportStagingObjectsInTheirOwnNamespace() throws Exception {
        String key = "69a45525-2f1f-4bba-8fb7-64dfcc3a41cf";
        byte[] content = "source workbook".getBytes(StandardCharsets.UTF_8);
        Path source = Files.createTempFile(storageDirectory, "upload-", ".xlsx");
        Files.write(source, content);

        storage.put(RfqObjectStorage.ObjectKind.IMPORT, key, source);
        try (InputStream staged = storage.open(RfqObjectStorage.ObjectKind.IMPORT, key)) {
            assertArrayEquals(content, staged.readAllBytes());
        }

        storage.delete(RfqObjectStorage.ObjectKind.IMPORT, key);
        assertThrows(RfqException.class, () -> storage.open(RfqObjectStorage.ObjectKind.IMPORT, key));
    }
}
