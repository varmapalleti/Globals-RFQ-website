package com.globalrfq.rfq.attachment.storage;

import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.config.RfqFileProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3RfqObjectStorageTest {
    @TempDir
    Path tempDirectory;

    @Test
    void writesObjectsUnderPrivateConfiguredNamespaces() throws Exception {
        S3Spy spy = new S3Spy();
        RfqFileProperties properties = new RfqFileProperties();
        properties.setS3Bucket("rfq-private-test");
        properties.setS3Prefix("staging/global-rfq");
        S3RfqObjectStorage storage = new S3RfqObjectStorage(spy.client(), properties);
        String key = "f1cc8e2e-7c2c-4bca-aabc-41c2890f9058";
        Path content = Files.writeString(tempDirectory.resolve("image.png"), "image bytes");

        storage.put(RfqObjectStorage.ObjectKind.ATTACHMENT, key, content);
        storage.put(RfqObjectStorage.ObjectKind.IMPORT, key, content);

        assertEquals(2, spy.puts.size());
        assertEquals("rfq-private-test", spy.puts.get(0).bucket());
        assertEquals("staging/global-rfq/attachments/" + key, spy.puts.get(0).key());
        assertEquals("staging/global-rfq/imports/" + key + ".stage", spy.puts.get(1).key());
    }

    @Test
    void rejectsUntrustedObjectKeysBeforeCallingAws() {
        S3Spy spy = new S3Spy();
        S3RfqObjectStorage storage = new S3RfqObjectStorage(spy.client(), new RfqFileProperties());

        assertThrows(RfqException.class,
                () -> storage.delete(RfqObjectStorage.ObjectKind.ATTACHMENT, "../../outside"));
        assertTrue(spy.puts.isEmpty());
        assertEquals(0, spy.calls);
    }

    private static final class S3Spy {
        private final List<PutObjectRequest> puts = new ArrayList<>();
        private int calls;

        private S3Client client() {
            return (S3Client) Proxy.newProxyInstance(S3Client.class.getClassLoader(), new Class<?>[]{S3Client.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("putObject")) {
                            calls++;
                            puts.add((PutObjectRequest) args[0]);
                            return PutObjectResponse.builder().build();
                        }
                        if (method.getName().equals("close")) return null;
                        if (method.getName().equals("toString")) return "S3Spy";
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("equals")) return proxy == args[0];
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
