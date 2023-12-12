package org.example;

import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class HandlerTest {
    @Test
    void sendRequest() throws IOException {
        byte[] content = readFile("Lennox Family Trust - cut down.pdf");
        new Handler().sendRequest(content);
    }

    private static byte[] readFile(String fileName) throws IOException {
        InputStream is = HandlerTest.class.getResourceAsStream("/" + fileName);
        if (is == null) {
            return null;
        }

        try (BufferedInputStream bis = new BufferedInputStream(is);
             ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            for (int result = bis.read(); result != -1; result = bis.read()) {
                buf.write((byte) result);
            }


            return buf.toByteArray();
        }
    }
}
