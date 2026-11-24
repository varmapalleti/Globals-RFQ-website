package com.globalrfq.rfq.attachment.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/** Durable byte storage for RFQ attachments and staged imports. */
public interface RfqObjectStorage {
    void put(ObjectKind kind, String key, Path source) throws IOException;

    InputStream open(ObjectKind kind, String key) throws IOException;

    void delete(ObjectKind kind, String key);

    enum ObjectKind {
        ATTACHMENT,
        IMPORT
    }
}
