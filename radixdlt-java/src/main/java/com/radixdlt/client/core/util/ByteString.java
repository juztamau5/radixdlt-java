package com.radixdlt.client.core.util;

public final class ByteString {
    private ByteString() {
    }

    /**
     * Converts a byte array to a hex string
     *
     * @param data The byte array
     * @return The hex string with lower-case alpha digits
     */
    public static String toHex(byte[] data) {
        StringBuffer result = new StringBuffer(data.length * 2);

        for (int i = 0; i < data.length; i += 2) {
            result.append(HEX_DIGITS[(data[i] >> 4) & 0xf]);
            result.append(HEX_DIGITS[data[i + 1] & 0xf]);
        }

        return new String(result);
    }

    /**
     * Converts a hex string to a byte array
     *
     * @param hex The string of hex characters using capital or lower-case digits
     * @return The converted byte array
     */
    public static byte[] decodeHex(String hex) {
        byte[] result = new byte[hex.length() / 2];

        for (int i = 0; i < result.length; i++) {
            byte digit1 = (byte) (decodeHexDigit(hex.charAt(i * 2)) << 4);
            byte digit2 = decodeHexDigit(hex.charAt(i * 2 + 1));
            result[i] = (byte) (digit1 + digit2);
        }

        return result;
    }

    /**
     * Converts a hex digit to a byte
     *
     * @param c The hex digit, with alpha digits being capital or lower-case
     * @return The converted byte
     */
    private static byte decodeHexDigit(char c) {
        if ('0' <= c && c <= '9')
            return (byte) (c - '0');
        else if ('a' <= c && c <= 'f')
            return (byte) (c - 'a' + 10);
        else if ('A' <= c && c <= 'F')
            return (byte) (c - 'A' + 10);

        throw new IllegalArgumentException("Unexpected hex digit: " + c);
    }

    private static char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
}
