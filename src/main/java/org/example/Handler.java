package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.S3Object;
import software.amazon.awssdk.services.textract.model.*;

import java.util.List;
import java.util.UUID;

public class Handler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Handler.class);
    private final String bucket;

    public Handler() {
        String targetBucket = "bucket" + System.currentTimeMillis();
        try (S3Client s3Client = DependencyFactory.s3Client()) {
            createBucket(s3Client, targetBucket);
            bucket = targetBucket;
        }
    }

    public void sendRequest() {
        sendRequest("Testing with the {sdk-java}".getBytes());
    }

    public void sendRequest(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            LOGGER.debug("requested content is null or empty");
            return;
        }

        if (bucket.isEmpty()) {
            LOGGER.debug("s3 bucket is not initialized");
            return;
        }

        String key = UUID.randomUUID().toString();

        try (S3Client s3Client = DependencyFactory.s3Client()) {
            try (TextractClient textractClient = TextractClient.create()) {
                LOGGER.info("Uploading object to {}:...", key);
                s3Client.putObject(
                        PutObjectRequest.builder().bucket(bucket).key(key).build(),
                        RequestBody.fromBytes(bytes)
                );

                LOGGER.info("Upload complete");

                boolean isAnalyzeSuccess = analyzeDocument(textractClient, bucket, key);
                LOGGER.info("Is Analyze Success: {}", isAnalyzeSuccess);

                LOGGER.info("Closing the connection to S3 and Textract");
            } finally {
                cleanUp(s3Client, bucket, key);
            }
        } finally {
            LOGGER.info("Connections closed");
            LOGGER.info("Exiting...");
        }
    }

    private void createBucket(S3Client s3Client, String bucketName) {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            LOGGER.info("Creating bucket: {}", bucketName);
            s3Client.waiter().waitUntilBucketExists(HeadBucketRequest.builder().bucket(bucketName).build());
            LOGGER.info("{} is ready", bucketName);
        } catch (S3Exception e) {
            LOGGER.error("Failed to create bucket", e);
        }
    }

    private boolean analyzeDocument(TextractClient textractClient, String bucketName, String keyName) {
        LOGGER.info("Analyzing document...");
        try {
            S3Object s3ObjectTextract = S3Object.builder().bucket(bucketName).name(keyName).build();
            DocumentLocation documentLocation = DocumentLocation.builder()
                    .s3Object(s3ObjectTextract)
                    .build();

            StartDocumentTextDetectionResponse startResponse = textractClient.startDocumentTextDetection(
                    StartDocumentTextDetectionRequest.builder().documentLocation(documentLocation).build()
            );

            String jobId = startResponse.jobId();

            GetDocumentTextDetectionResponse response;
            while (true) {
                response = textractClient.getDocumentTextDetection(
                        GetDocumentTextDetectionRequest.builder().jobId(jobId).build()
                );

                JobStatus jobStatus = response.jobStatus();
                LOGGER.info("Job Status: {}", jobStatus);

                if (jobStatus == JobStatus.SUCCEEDED || jobStatus == JobStatus.FAILED) {
                    break;
                } else {
                    sleep();
                }
            }

            // Process the results as needed
            LOGGER.info("Document analysis complete. Results:");
            if (response.hasBlocks()) {
                List<Block> blocks = response.blocks();
                LOGGER.info("Detected blocks: {}", blocks.toString());
            } else {
                LOGGER.info("No blocks detected");
            }

            return true;
        } catch (TextractException e) {
            LOGGER.error("Fail to extract document", e);
        } catch (S3Exception e) {
            LOGGER.error("Fail to create S3 bucket", e);
        }

        return false;
    }

    private static void sleep() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            // Ignore
        }
    }

    private void cleanUp(S3Client s3Client, String bucketName, String keyName) {
        LOGGER.info("Cleaning up...");
        try {
            LOGGER.info("Deleting object: " + keyName);
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucketName).key(keyName).build();
            s3Client.deleteObject(deleteObjectRequest);
            LOGGER.info(keyName + " has been deleted.");
            LOGGER.info("Deleting bucket: " + bucketName);
            DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder().bucket(bucketName).build();
            s3Client.deleteBucket(deleteBucketRequest);
            LOGGER.info(bucketName + " has been deleted.");

            LOGGER.info("Cleanup complete");
        } catch (S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
        }
    }
}
