package com.globalrfq.rfq.attachment.storage;

import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.config.RfqFileProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardCopyOption;

@Component
@ConditionalOnProperty(prefix = "app.rfq", name = "storage-provider", havingValue = "local", matchIfMissing = true)
public class LocalRfqObjectStorage implements RfqObjectStorage {
    private final RfqFileProperties properties;

    public LocalRfqObjectStorage(RfqFileProperties properties) {
        this.properties = properties;
    }

    @Override
    public void put(ObjectKind kind, String key, Path source) throws IOException {
        Path target = pathFor(kind, key);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), key, ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public InputStream open(ObjectKind kind, String key) throws IOException {
        Path path = pathFor(kind, key);
        if (!Files.isRegularFile(path)) {
            throw new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "The stored RFQ file is unavailable");
        }
        return Files.newInputStream(path);
    }

    @Override
    public void delete(ObjectKind kind, String key) {
        try {
            Files.deleteIfExists(pathFor(kind, key));
        } catch (IOException exception) {
            throw new IllegalStateException("The stored RFQ file could not be deleted", exception);
        }
    }

    private Path pathFor(ObjectKind kind, String key) {
        requireUuid(key);
        Path root = properties.getStorageDirectory().toAbsolutePath().normalize();
        // Keep the existing attachment key layout so already-uploaded local files remain readable.
        Path path = kind == ObjectKind.ATTACHMENT
                ? root.resolve(key)
                : root.resolve("imports").resolve(key + ".stage");
        path = path.normalize();
        if (!path.startsWith(root)) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "RFQ storage key is invalid");
        }
        return path;
    }

    static void requireUuid(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}")) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "RFQ storage key is invalid");
        }
    }
}
