package edu.northeastern.coldstorage.view.web;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * A very small JSON builder, so the web dashboard needs no external library.
 *
 * <p>Usage: {@code new JsonWriter().beginObject().name("a").value(1).endObject().toString()}
 * produces {@code {"a":1}}. Commas are inserted automatically. NaN and infinite numbers
 * (for example the mean of a room with no readings) are written as {@code null},
 * because JSON has no way to represent them.
 */
public class JsonWriter {

    private final StringBuilder out = new StringBuilder();
    /** For each open object/array: true until its first element is written (no comma needed yet). */
    private final Deque<Boolean> firstElement = new ArrayDeque<>();
    /** True right after name(...), so the next value must not be preceded by a comma. */
    private boolean afterName;

    public JsonWriter beginObject() {
        beforeValue();
        out.append('{');
        firstElement.push(true);
        return this;
    }

    public JsonWriter endObject() {
        firstElement.pop();
        out.append('}');
        return this;
    }

    public JsonWriter beginArray() {
        beforeValue();
        out.append('[');
        firstElement.push(true);
        return this;
    }

    public JsonWriter endArray() {
        firstElement.pop();
        out.append(']');
        return this;
    }

    /** Writes an object key; the next call must write its value. */
    public JsonWriter name(String name) {
        beforeValue();
        writeString(name);
        out.append(':');
        afterName = true;
        return this;
    }

    public JsonWriter value(String value) {
        beforeValue();
        if (value == null) {
            out.append("null");
        } else {
            writeString(value);
        }
        return this;
    }

    public JsonWriter value(long value) {
        beforeValue();
        out.append(value);
        return this;
    }

    /** Writes a number with up to 3 decimals; NaN and infinity become null. */
    public JsonWriter value(double value) {
        beforeValue();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            out.append("null");
        } else {
            out.append(String.format(Locale.ROOT, "%.3f", value));
        }
        return this;
    }

    public JsonWriter value(boolean value) {
        beforeValue();
        out.append(value);
        return this;
    }

    /** Writes a JSON null. */
    public JsonWriter nullValue() {
        beforeValue();
        out.append("null");
        return this;
    }

    @Override
    public String toString() {
        return out.toString();
    }

    /** Adds a comma between elements, except before the first one and right after a key. */
    private void beforeValue() {
        if (afterName) {
            afterName = false;
            return;
        }
        if (!firstElement.isEmpty()) {
            if (firstElement.peek()) {
                firstElement.pop();
                firstElement.push(false);
            } else {
                out.append(',');
            }
        }
    }

    private void writeString(String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c)); // other control characters
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }
}
