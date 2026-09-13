package com.devfat.mini_ecommerce.storage.internal;

import com.devfat.mini_ecommerce.storage.StorageService;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.errors.MinioException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private final MinioClient minioClient;
    private final FileValidationUtil fileValidationUtil;

    @Value("${app.minio.bucket}")
    private String bucketName;

    @Value("${app.minio.endpoint}")
    private String endPoint;

    @Value("${app.minio.public-url:${app.minio.endpoint}}")
    private String publicUrl;

    /**
     * Tự động khởi tạo Bucket & cấu hình Policy PUBLIC READ cho MinIO khi ứng dụng khởi động.
     * Giải quyết triệt để lỗi 403 Forbidden khi Frontend hiển thị hình ảnh.
     */
    @PostConstruct
    public void initBucket() {
        try {
            boolean found = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );

            if (!found) {
                log.info("📦 Bucket '{}' chưa tồn tại trên MinIO. Đang tiến hành tạo mới...", bucketName);
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("✅ Tạo thành công bucket MinIO: {}", bucketName);
            }

            // Cấu hình Bucket Policy: Public Read cho toàn bộ object (GET Object)
            String policyJson = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [
                        {
                          "Effect": "Allow",
                          "Principal": {"AWS": ["*"]},
                          "Action": ["s3:GetObject"],
                          "Resource": ["arn:aws:s3:::%s/*"]
                        }
                      ]
                    }
                    """.formatted(bucketName);

            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .config(policyJson)
                            .build()
            );
            log.info("🔓 Cấu hình MinIO Bucket '{}' sang chế độ PUBLIC READ thành công!", bucketName);

        } catch (Exception e) {
            log.error("⚠️ Không thể khởi tạo/cấu hình Bucket Policy MinIO: {}", e.getMessage(), e);
        }
    }

    private byte[] resizeImage(MultipartFile file) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Thumbnails.of(file.getInputStream())
                .size(800, 800)
                .outputQuality(0.85)
                .toOutputStream(outputStream);
        return outputStream.toByteArray();
    }

    @Override
    public String uploadFile(MultipartFile file, String folder, boolean resizeImage) {
        fileValidationUtil.validateImageFile(file);

        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String cleanExtension = (extension != null && !extension.isBlank()) ? extension.toLowerCase() : "jpg";
        String objectName = folder + "/" + UUID.randomUUID() + "." + cleanExtension;
        try {
            byte[] dataToUpload = resizeImage
                    ? resizeImage(file)
                    : file.getBytes();

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(dataToUpload), dataToUpload.length, -1)
                            .contentType(file.getContentType())
                            .build()
            );
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        } catch (MinioException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }

        if (publicUrl != null && !publicUrl.isBlank()) {
            String trimmedPublicUrl = StringUtils.trimTrailingCharacter(publicUrl.trim(), '/');
            if (trimmedPublicUrl.contains("r2.dev") || !trimmedPublicUrl.contains("localhost")) {
                return trimmedPublicUrl + "/" + objectName;
            }
            return trimmedPublicUrl + "/" + bucketName + "/" + objectName;
        }
        String baseUrl = StringUtils.trimTrailingCharacter(endPoint.trim(), '/');
        return baseUrl + "/" + bucketName + "/" + objectName;
    }
}
