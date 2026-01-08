package com.QueueUp.Backend.controller;

import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/uploads/s3")
public class S3Controller {

    private final S3Presigner s3Presigner;

    @Value("${aws.s3-bucket}")
    private String bucketName;

    public S3Controller(S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
    }

    @PostMapping("/presign")
    public ResponseEntity<?> generatePresignedUrl(@RequestBody Map<String, String> body,
                                                  jakarta.servlet.http.HttpServletRequest request) {
        String name = body.get("name");
        String type = body.get("type");
        Long userId = (Long) request.getAttribute("userId");

        if (name == null || type == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "name and type required"));
        }

        String key = userId + "/chat_attachments/" + System.currentTimeMillis() + "_" + name;

        try {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(type)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(2))
                    .putObjectRequest(objectRequest)
                    .build();

            String url = s3Presigner.presignPutObject(presignRequest).url().toString();

            return ResponseEntity.ok(Map.of("url", url, "key", key));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not generate presigned URL"));
        }
    }

    @PostMapping("/presign-download")
    public ResponseEntity<?> generatePresignedDownloadUrl(@RequestBody Map<String, Object> body) {
        String key = (String) body.get("key");
        int expiresIn = Math.min((Integer) body.getOrDefault("expiresIn", 60), 900);

        if (key == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "key required"));
        }

        try {
            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expiresIn))
                    .getObjectRequest(objectRequest)
                    .build();

            String url = s3Presigner.presignGetObject(presignRequest).url().toString();

            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not generate presigned URL"));
        }
    }
}