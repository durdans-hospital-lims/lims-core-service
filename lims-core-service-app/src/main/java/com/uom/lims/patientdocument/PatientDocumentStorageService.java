package com.uom.lims.patientdocument;

import com.uom.lims.api.common.enums.DocumentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import java.io.IOException;
import java.time.Year;
import java.util.UUID;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import java.time.Duration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Slf4j
@Service
public class PatientDocumentStorageService {

        private final S3Client s3Client;
        private final S3Presigner s3Presigner;

        @Value("${spring.cloud.aws.s3.bucket}")
        private String bucketName;

        public PatientDocumentStorageService(S3Client s3Client,
                        S3Presigner s3Presigner,
                        @Value("${spring.cloud.aws.s3.bucket}") String bucketName) {
                this.s3Client = s3Client;
                this.s3Presigner = s3Presigner;
                this.bucketName = bucketName;
        }

        public String uploadFile(String patientCode,
                        DocumentType documentType,
                        MultipartFile file) throws IOException {

                String uniqueFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

                String s3Key = String.format(
                                "patients/%s/%s/%s/%s",
                                patientCode,
                                Year.now().getValue(),
                                documentType.name(),
                                uniqueFileName);

                log.info("Preparing to upload file to S3 - Bucket: {}, Key: {}, Size: {} bytes, ContentType: {}",
                                bucketName, s3Key, file.getSize(), file.getContentType());

                try {
                        PutObjectRequest putRequest = PutObjectRequest.builder()
                                        .bucket(bucketName)
                                        .key(s3Key)
                                        .contentType(file.getContentType())
                                        .build();

                        PutObjectResponse response = s3Client.putObject(
                                        putRequest,
                                        RequestBody.fromBytes(file.getBytes()));

                        log.info("File uploaded successfully to S3 - ETag: {}, Key: {}", response.eTag(), s3Key);
                        return s3Key;

                } catch (Exception e) {
                        log.error("Failed to upload file to S3 - Bucket: {}, Key: {}, Error: {}",
                                        bucketName, s3Key, e.getMessage(), e);
                        throw e;
                }
        }

        public String generatePresignedUrl(String s3Key, Duration duration) {

                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                                .bucket(bucketName)
                                .key(s3Key)
                                .build();

                GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                                .signatureDuration(duration)
                                .getObjectRequest(getObjectRequest)
                                .build();

                PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

                return presignedRequest.url().toString();
        }

        public void deleteFile(String s3Key) {
                if (s3Key == null || s3Key.isBlank()) {
                        return;
                }

                try {
                        log.info("Deleting file from S3 - Bucket: {}, Key: {}", bucketName, s3Key);

                        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                                        .bucket(bucketName)
                                        .key(s3Key)
                                        .build();

                        s3Client.deleteObject(deleteRequest);

                        log.info("File deleted successfully from S3 - Key: {}", s3Key);

                } catch (Exception e) {
                        log.error("Failed to delete file from S3 - Bucket: {}, Key: {}, Error: {}",
                                        bucketName, s3Key, e.getMessage(), e);
                }
        }

}
