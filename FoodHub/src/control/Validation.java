package control;

final class Validation {
    private Validation() {}

    static String text(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " must contain 1 to " + maxLength + " characters.");
        }
        return value.trim();
    }

    static int positive(int value, String field) {
        if (value <= 0) throw new IllegalArgumentException(field + " must be positive.");
        return value;
    }
}
