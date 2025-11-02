package com.example.email.grpc;

/**
 * Performs a basic +1 ASCII shift to demonstrate encryption.
 */
public final class SimpleEncryptor {
    private SimpleEncryptor() {
    }

    public static String shiftByOne(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(input.length());
        for (char ch : input.toCharArray()) {
            builder.append((char) (ch + 1));
        }
        return builder.toString();
    }
}
