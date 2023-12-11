package org.example;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.io.ByteArrayInputStream;

public class Handler {
    private final S3Client s3Client;
    private final TextractClient textractClient;

    public Handler() {
        s3Client = DependencyFactory.s3Client();
        textractClient = TextractClient.create();
    }

    public void sendRequest() {
        String bucket = "bucket" + System.currentTimeMillis();
        String key = "key";

        createBucket(s3Client, bucket);

        System.out.println("Uploading object...");
        s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                        .build(),
                RequestBody.fromString("Testing with the {sdk-java}"));

        System.out.println("Upload complete");
        System.out.printf("%n");

        analyzeDocument(textractClient, bucket, key);

        cleanUp(s3Client, bucket, key);

        System.out.println("Closing the connection to {S3} and Textract");
        s3Client.close();
        textractClient.close();
        System.out.println("Connections closed");
        System.out.println("Exiting...");
    }

    public static void createBucket(S3Client s3Client, String bucketName) {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            System.out.println("Creating bucket: " + bucketName);
            s3Client.waiter().waitUntilBucketExists(HeadBucketRequest.builder().bucket(bucketName).build());
            System.out.println(bucketName + " is ready.");
            System.out.printf("%n");
        } catch (S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public static void analyzeDocument(TextractClient textractClient, String bucketName, String keyName) {
        System.out.println("Analyzing document...");
        try {
            S3Object s3Object = S3Object.builder().bucket(bucketName).name(keyName).build();
            Document document = Document.builder().s3Object(s3Object).build();

            StartDocumentTextDetectionResponse startResponse = textractClient.startDocumentTextDetection(
                    StartDocumentTextDetectionRequest.builder()
                            .documentLocation(document)
                            .build());

            String jobId = startResponse.jobId();

            // Wait for the document analysis job to complete
            textractClient.waiter().waitUntilDocumentTextDetectionComplete(
                    DescribeDocumentTextDetectionRequest.builder().jobId(jobId).build());

            // Retrieve the results
            GetDocumentTextDetectionResponse response = textractClient.getDocumentTextDetection(
                    GetDocumentTextDetectionRequest.builder().jobId(jobId).build());

            // Process the results as needed
            System.out.println("Document analysis complete. Results:");
            System.out.println(response.blocks());

            System.out.printf("%n");
        } catch (TextractException | S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public static void cleanUp(S3Client s3Client, String bucketName, String keyName) {
        System.out.println("Cleaning up...");
        try {
            System.out.println("Deleting object: " + keyName);
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucketName).key(keyName).build();
            s3Client.deleteObject(deleteObjectRequest);
            System.out.println(keyName + " has been deleted.");
            System.out.println("Deleting bucket: " + bucketName);
            DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder().bucket(bucketName).build();
            s3Client.deleteBucket(deleteBucketRequest);
            System.out.println(bucketName + " has been deleted.");
            System.out.printf("%n");
        } catch (S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        System.out.println("Cleanup complete");
        System.out.printf("%n");
    }
}
