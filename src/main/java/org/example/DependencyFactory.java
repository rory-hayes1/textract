
package org.example;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * The module containing all dependencies required by the {@link Handler}.
 */
public class DependencyFactory {
    private static final Region region = Region.US_EAST_1;
    private static final String accessKey = "your-access-key";
    private static final String secret = "your-secret-key";

    private DependencyFactory() {
    }

    static AwsBasicCredentials credentials() {
        return AwsBasicCredentials.create(accessKey, secret);
    }

    /**
     * @return an instance of S3Client
     */
    public static S3Client s3Client() {
        return S3Client.builder()
                .region(region) // replace with your desired region
                .credentialsProvider(StaticCredentialsProvider.create(credentials()))
                .build();
    }
}
