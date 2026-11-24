package com.globalrfq.rfq.intake.service;

import com.globalrfq.rfq.attachment.storage.LocalRfqObjectStorage;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.config.RfqFileProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RfqImportPreviewStorageTest {
    @TempDir
    Path storageDirectory;

    private RfqImportPreviewStorage storage;

    @BeforeEach
    void setUp() {
        RfqFileProperties properties = new RfqFileProperties();
        properties.setStorageDirectory(storageDirectory);
        storage = new RfqImportPreviewStorage(properties, new LocalRfqObjectStorage(properties));
    }

    @Test
    void stagedImportCanBeVerifiedAndRemoved() throws Exception {
        byte[] content = "staged source file".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "customer-rfq.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);

        var stored = storage.store(file);
        Path verified = storage.verifiedPath(stored.key(), stored.sha256());
        try {
            assertEquals("staged source file", Files.readString(verified));
        } finally {
            storage.deleteVerifiedCopy(verified);
        }

        storage.delete(stored.key());
        assertThrows(RfqException.class, () -> storage.verifiedPath(stored.key(), stored.sha256()));
    }

    @Test
    void rejectsStagedFileWhenExpectedDigestDoesNotMatch() {
        MockMultipartFile file = new MockMultipartFile("file", "customer-rfq.xlsx", "application/octet-stream",
                "source file".getBytes(StandardCharsets.UTF_8));
        var stored = storage.store(file);

        assertThrows(RfqException.class, () -> storage.verifiedPath(stored.key(), "0".repeat(64)));
        storage.delete(stored.key());
    }
}
