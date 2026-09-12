package com.loan.infrastructure.oss;

import lombok.Value;
import java.io.InputStream;

@Value
public class OssObjectOpenResult {
    InputStream inputStream;
    long contentLength;
    String contentType;
}
