package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Paths;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.S3Object;
import software.amazon.awssdk.services.textract.model.*;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;




public class Handler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Handler.class);

    private TextractClient textractClient;

    public void sendRequest() {
        String bucket = "bucket" + System.currentTimeMillis();
        String key = "key";
    
        // Replace the following line with your actual AWS access key and secret key
        AwsBasicCredentials credentials = AwsBasicCredentials.create("your-access-key", "your-secret-key");
    
        try (S3Client s3Client = S3Client.builder()
                .region(Region.US_EAST_1) // replace with your desired region
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build()) {
    
            createBucket(s3Client, bucket);
    
            System.out.println("Uploading object...");
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).build(),
                    RequestBody.fromString("Testing with the {sdk-java}")
            );
    
            System.out.println("Upload complete");
    
            boolean isAnalyzeSuccess = analyzeDocument(textractClient, bucket, key);
            System.out.println("Is Analyze Success: " + isAnalyzeSuccess);
    
            System.out.println("Cleaning up...");
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            s3Client.deleteBucket(DeleteBucketRequest.builder().bucket(bucket).build());
            System.out.println("Cleanup complete");
    
        } finally {
            System.out.println("Connections closed");
            System.out.println("Exiting...");
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

            while (true) {
                GetDocumentTextDetectionResponse response = textractClient.getDocumentTextDetection(
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
            LOGGER.info(
                    "Detected blocks: {}",
                    textractClient.getDocumentTextDetection(
                            GetDocumentTextDetectionRequest.builder().jobId(jobId).build()
                    ).blocks().toString()
            );

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
