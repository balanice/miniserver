package com.force.miniserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    public static int byteArrayToInt(byte[] b) {
        return (b[0] & 0xFF) |
                (b[1] & 0xFF) << 8 |
                (b[2] & 0xFF) << 16 |
                (b[3] & 0xFF) << 24;
    }

    public static byte[] intToByteArray(int a) {
        return new byte[]{
                (byte) (a & 0xFF),
                (byte) ((a >> 8) & 0xFF),
                (byte) ((a >> 16) & 0xFF),
                (byte) ((a >> 24) & 0xFF)
        };
    }

    public static boolean empty(String s) {
        return s == null || s.equals("");
    }

    public static boolean resourceToFile(String resourcePath, String filePath) {
        File file = new File(filePath);
        if (file.exists() && file.isFile()) {
            logger.info("file exists: {}", filePath);
            return true;
        }
        ClassPathResource resource = new ClassPathResource(resourcePath);
        File parent = file.getParentFile();
        logger.info(parent.getAbsolutePath());
        if (!parent.mkdirs()) {
            logger.error("make dirs failed");
            return false;
        }
        boolean result = false;
        FileOutputStream outputStream = null;
        InputStream inputStream = null;
        byte[] bytes = new byte[1024 * 512];
        int len;
        try {
            inputStream = resource.getInputStream();
            outputStream = new FileOutputStream(file);
            while ((len = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, len);
            }
            result = true;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                if (outputStream != null) outputStream.close();
                if (inputStream != null) inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }
}
