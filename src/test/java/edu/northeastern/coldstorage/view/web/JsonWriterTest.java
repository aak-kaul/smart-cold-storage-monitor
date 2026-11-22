package edu.northeastern.coldstorage.view.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JsonWriterTest {

    @Test
    void writesAnObjectWithCommas() {
        String json = new JsonWriter().beginObject()
                .name("room").value("A")
                .name("count").value(3)
                .name("ok").value(true)
                .endObject().toString();
        assertEquals("{\"room\":\"A\",\"count\":3,\"ok\":true}", json);
    }

    @Test
    void writesNestedArraysAndObjects() {
        String json = new JsonWriter().beginObject()
                .name("list").beginArray().value(1).value(2).endArray()
                .name("empty").beginArray().endArray()
                .name("inner").beginObject().name("x").nullValue().endObject()
                .endObject().toString();
        assertEquals("{\"list\":[1,2],\"empty\":[],\"inner\":{\"x\":null}}", json);
    }

    @Test
    void arrayOfObjectsIsCommaSeparated() {
        String json = new JsonWriter().beginArray()
                .beginObject().name("a").value(1).endObject()
                .beginObject().name("a").value(2).endObject()
                .endArray().toString();
        assertEquals("[{\"a\":1},{\"a\":2}]", json);
    }

    @Test
    void decimalsUseThreePlacesAndADot() {
        assertEquals("[3.142,-0.500]", new JsonWriter().beginArray().value(3.14159).value(-0.5).endArray().toString());
    }

    @Test
    void nanAndInfinityBecomeNull() {
        String json = new JsonWriter().beginArray()
                .value(Double.NaN).value(Double.POSITIVE_INFINITY).endArray().toString();
        assertEquals("[null,null]", json);
    }

    @Test
    void specialCharactersAreEscaped() {
        String json = new JsonWriter().value("say \"hi\"\\\n\t" + (char) 1).toString();
        assertEquals("\"say \\\"hi\\\"\\\\\\n\\t\\u0001\"", json);
    }

    @Test
    void nullStringIsWrittenAsNull() {
        assertEquals("null", new JsonWriter().value((String) null).toString());
    }
}
