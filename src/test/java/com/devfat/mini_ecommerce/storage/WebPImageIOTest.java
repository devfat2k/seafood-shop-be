package com.devfat.mini_ecommerce.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.util.Arrays;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPImageIOTest {

    @Test
    @DisplayName("TwelveMonkeys should register WebP reader in ImageIO SPI")
    void shouldRegisterWebPReader() {
        String[] readerFormatNames = ImageIO.getReaderFormatNames();
        boolean hasWebPReader = Arrays.stream(readerFormatNames)
                .anyMatch(name -> name.equalsIgnoreCase("webp"));

        assertTrue(hasWebPReader, "ImageIO should have a registered reader for WebP format");

        Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReadersByFormatName("webp");
        assertTrue(readers.hasNext(), "ImageIO.getImageReadersByFormatName(\"webp\") should have at least one reader");
    }
}
