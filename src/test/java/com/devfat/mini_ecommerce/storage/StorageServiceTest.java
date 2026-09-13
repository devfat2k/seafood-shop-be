package com.devfat.mini_ecommerce.storage;

import com.devfat.mini_ecommerce.storage.internal.FileValidationUtil;
import com.devfat.mini_ecommerce.storage.internal.StorageServiceImpl;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private FileValidationUtil fileValidationUtil;

    private StorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        storageService = new StorageServiceImpl(minioClient, fileValidationUtil);
        ReflectionTestUtils.setField(storageService, "bucketName", "mini-ecommerce");
        ReflectionTestUtils.setField(storageService, "endPoint", "http://minio:9000");
    }

    @Test
    @DisplayName("Should use publicUrl instead of internal endpoint when publicUrl is configured")
    void shouldReturnPublicUrlWhenConfigured() throws Exception {
        ReflectionTestUtils.setField(storageService, "publicUrl", "http://localhost:9000");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-image.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        doNothing().when(fileValidationUtil).validateImageFile(file);

        String resultUrl = storageService.uploadFile(file, "productImage", false);

        assertTrue(resultUrl.startsWith("http://localhost:9000/mini-ecommerce/productImage/"));
        assertTrue(resultUrl.endsWith(".png"));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("Should strip trailing slash from publicUrl")
    void shouldStripTrailingSlashFromPublicUrl() {
        ReflectionTestUtils.setField(storageService, "publicUrl", "http://localhost:9000/");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        doNothing().when(fileValidationUtil).validateImageFile(file);

        String resultUrl = storageService.uploadFile(file, "categoryImage", false);

        assertTrue(resultUrl.startsWith("http://localhost:9000/mini-ecommerce/categoryImage/"));
    }

    @Test
    @DisplayName("Should fallback to endPoint when publicUrl is null or empty")
    void shouldFallbackToEndPointWhenPublicUrlEmpty() {
        ReflectionTestUtils.setField(storageService, "publicUrl", "");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "banner.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );
        doNothing().when(fileValidationUtil).validateImageFile(file);

        String resultUrl = storageService.uploadFile(file, "bannerHeroImage", false);

        assertTrue(resultUrl.startsWith("http://minio:9000/mini-ecommerce/bannerHeroImage/"));
    }

    @Test
    @DisplayName("Should normalize uppercase file extension and handle files without extension")
    void shouldNormalizeFileExtensions() throws Exception {
        ReflectionTestUtils.setField(storageService, "publicUrl", "http://localhost:9000");

        MockMultipartFile fileUppercase = new MockMultipartFile(
                "file", "PHOTO.PNG", "image/png", new byte[]{1, 2, 3}
        );
        doNothing().when(fileValidationUtil).validateImageFile(fileUppercase);
        String urlUpper = storageService.uploadFile(fileUppercase, "productImage", false);
        assertTrue(urlUpper.endsWith(".png"));

        MockMultipartFile fileNoExt = new MockMultipartFile(
                "file", "avatar", "image/jpeg", new byte[]{1, 2, 3}
        );
        doNothing().when(fileValidationUtil).validateImageFile(fileNoExt);
        String urlNoExt = storageService.uploadFile(fileNoExt, "productImage", false);
        assertTrue(urlNoExt.endsWith(".jpg"));
    }
}
