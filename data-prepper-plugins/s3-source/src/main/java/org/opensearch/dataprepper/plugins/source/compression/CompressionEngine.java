/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.source.compression;

import java.io.IOException;
import java.io.InputStream;

public interface CompressionEngine {
    InputStream createInputStream(final String s3Key, final InputStream responseInputStream) throws IOException;
}
