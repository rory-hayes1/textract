package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.S3Object;
import software.amazon.awssdk.services.textract.model.*;

public class Handler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Handler.class);

    public void sendRequest() {
        String bucket = "bucket" + System.currentTimeMillis();
        String key = "key";

        try (S3Client s3Client = DependencyFactory.s3Client()) {
            createBucket(s3Client, bucket);
            try (TextractClient textractClient = TextractClient.create()) {
                LOGGER.info("Uploading object...");
                s3Client.putObject(
                        PutObjectRequest.builder().bucket(bucket).key(key).build(),
                        RequestBody.fromString("Testing with the {sdk-java}")
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
            LOGGER.info("Creating bucket: " + bucketName);
            s3Client.waiter().waitUntilBucketExists(HeadBucketRequest.builder().bucket(bucketName).build());
            LOGGER.info("{} is ready", bucketName);
        } catch (S3Exception e) {
            LOGGER.error("Failed to create bucket", e);
        }
    }

    private boolean analyzeDocument(TextractClient textractClient, String bucketName, String keyName) {
        LOGGER.info("Analyzing document...");
        try {
            S3Object s3Object = S3Object.builder().bucket(bucketName).name(keyName).build();
            Document document = Document.builder().s3Object(s3Object).build();

            AnalyzeDocumentRequest request = AnalyzeDocumentRequest.builder().document(document).build();
            AnalyzeDocumentResponse response = textractClient.analyzeDocument(request);

            // Process the results as needed
            LOGGER.info("Document analysis complete. Results:");
            LOGGER.info(response.blocks().toString());

            return true;
        } catch (TextractException e) {
            LOGGER.error("Fail to extract document", e);
        } catch (S3Exception e) {
            LOGGER.error("Fail to create S3 bucket", e);
        }

        return false;
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
