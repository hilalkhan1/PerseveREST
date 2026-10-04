package io.resttestgen.implementation.helper;

import okhttp3.HttpUrl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

public class TestFieldVariantSender {

    @Test
    public void testBodyVariantsCoverEveryField() {
        List<String> variants = FieldVariantSender.bodyVariants(
                "{\"name\":\"Leo\",\"age\":3,\"type\":{\"id\":1},\"tags\":[\"a\"],\"vip\":true}");
        // name 5, age 7, type 4, type.id 7, tags 4, tags[0] 5, vip 4
        Assertions.assertEquals(36, variants.size());
        Assertions.assertTrue(variants.contains("{\"name\":null,\"age\":3,\"type\":{\"id\":1},\"tags\":[\"a\"],\"vip\":true}"));
        Assertions.assertTrue(variants.contains("{\"name\":\"Leo\",\"age\":\"abc\",\"type\":{\"id\":1},\"tags\":[\"a\"],\"vip\":true}"));
        Assertions.assertTrue(variants.contains("{\"name\":\"Leo\",\"age\":3,\"type\":\"x\",\"tags\":[\"a\"],\"vip\":true}"));
        Assertions.assertTrue(variants.contains("{\"name\":\"Leo\",\"age\":3,\"type\":{\"id\":" + FieldVariantSender.HUGE_NUMBER +
                "},\"tags\":[\"a\"],\"vip\":true}"));
        Assertions.assertTrue(variants.contains("{\"name\":\"Leo\",\"age\":3,\"type\":{\"id\":1},\"tags\":{},\"vip\":true}"));
        Assertions.assertTrue(variants.contains("{\"name\":\"Leo\",\"age\":3,\"type\":{\"id\":1},\"tags\":[\"a\"],\"vip\":\"maybe\"}"));
        Assertions.assertTrue(variants.stream().anyMatch(v -> v.length() > FieldVariantSender.LONG_STRING_LENGTH));
    }

    @Test
    public void testNoHugeAmountsOfResources() {
        List<String> variants = FieldVariantSender.bodyVariants("{\"partitions_count\":3}");
        Assertions.assertTrue(variants.stream().noneMatch(v -> v.contains(FieldVariantSender.HUGE_NUMBER)));
        Assertions.assertTrue(variants.contains("{\"partitions_count\":-1}"));
    }

    @Test
    public void testSingleValueAndInvalidBodies() {
        List<String> variants = FieldVariantSender.bodyVariants("\"abc\"");
        Assertions.assertTrue(variants.contains("null"));
        Assertions.assertTrue(variants.contains("\"\""));
        Assertions.assertEquals(List.of(), FieldVariantSender.bodyVariants("{not json"));
    }

    @Test
    public void testAtMostMaxFields() {
        StringBuilder body = new StringBuilder("{");
        for (int i = 0; i < 30; i++) {
            body.append(i == 0 ? "" : ",").append("\"f").append(i).append("\":true");
        }
        List<String> variants = FieldVariantSender.bodyVariants(body.append("}").toString());
        // 4 variants per boolean field, for the first MAX_FIELDS fields only
        Assertions.assertEquals(4 * FieldVariantSender.MAX_FIELDS, variants.size());
    }

    @Test
    public void testQueryVariants() {
        List<HttpUrl> urls = FieldVariantSender.queryVariants(HttpUrl.get("http://localhost:8080/pets?limit=10&name=x"));
        // limit: empty, long, abc, huge, decimal, negative; name: empty, long, number (the order of the parameters
        // may change)
        Assertions.assertEquals(9, urls.size());
        Assertions.assertTrue(urls.stream().anyMatch(u -> "abc".equals(u.queryParameter("limit")) && "x".equals(u.queryParameter("name"))));
        Assertions.assertTrue(urls.stream().anyMatch(u -> "10".equals(u.queryParameter("limit")) && "123456789".equals(u.queryParameter("name"))));
        Assertions.assertTrue(urls.stream().allMatch(u -> u.encodedPath().equals("/pets")));
    }

    @Test
    public void testPathVariants() {
        List<String> urls = FieldVariantSender.pathVariants(HttpUrl.get("http://localhost:8080/pets/5/visits"), "/pets/{petId}/visits")
                .stream().map(HttpUrl::toString).collect(Collectors.toList());
        // long, abc, huge, decimal, negative (the empty value would change the endpoint)
        Assertions.assertEquals(5, urls.size());
        Assertions.assertTrue(urls.contains("http://localhost:8080/pets/abc/visits"));
        Assertions.assertTrue(urls.contains("http://localhost:8080/pets/-1/visits"));
        Assertions.assertTrue(urls.stream().allMatch(u -> u.endsWith("/visits")));
    }
}
