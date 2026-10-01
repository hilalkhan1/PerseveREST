package io.resttestgen.core.helper;

// Modified for PerseveREST (REST League 2027): testIsoDatesAndDateTimes(). See NOTICE.

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestExtendedRandom {

    ExtendedRandom random = new ExtendedRandom();

    @Test
    public void testStringGeneration() {
        for (int i = 0; i < 100; i++) {
            System.out.println(random.nextRandomString(random.nextLength(5, 100)));
        }
    }

    @Test
    public void testLengthGeneration() {
        for (int i = 0; i < 100; i++) {
            System.out.println(random.nextLength(1, 400));
        }
    }

    @Test
    public void testShortLengthGeneration() {
        for (int i = 0; i < 100; i++) {
            System.out.println(random.nextShortLength(1, 50));
        }
    }

    @Test
    public void testWord() {
        for (int i = 0; i < 10; i++) {
            System.out.println(random.nextWord());
        }

        for (int i = 1; i < 10; i++) {
            String string = random.nextWord(i);
            System.out.println(string);
            Assertions.assertEquals(string.length(), i);
        }
    }

    @Test
    public void testDate() {
        for (int i = 0; i < 10; i++) {
            System.out.println(random.nextDate());
        }
    }

    @Test
    public void testDateTime() {
        for (int i = 0; i < 10; i++) {
            System.out.println(random.nextDateTime());
        }
    }

    @Test
    public void testBinaryString() {
        for (int i = 0; i < 10; i++) {
            System.out.println(random.nextBinaryString());
        }

        for (int i = 1; i < 10; i++) {
            String string = random.nextBinaryString(i);
            System.out.println(string);
            Assertions.assertEquals(string.length(), i);
        }
    }

    @Test
    public void testBase64() {
        for (int i = 0; i < 10; i++) {
            System.out.println(random.nextBase64());
        }
    }

    @Test
    public void testUUID() {
        for (int i = 0; i < 10; i++) {
            System.out.println(random.nextUUID());
        }
    }

    @Test
    public void testHash() {
        for (int i = 0; i < 10; i++) {
            System.out.println(random.nextHex());
        }

        for (int i = 0; i < 10; i++) {
            String string = random.nextHex(i);
            System.out.println(string);
            Assertions.assertEquals(string.length(), i);
        }
    }

    @Test
    public void testIBAN() {
        for (int i = 0; i < 10; i++) {
            System.out.println(random.nextIBAN());
        }
    }

    @Test
    public void testSSN() {
        for (int i = 0; i < 10; i++) {
            System.out.println(random.nextSSN());
        }
    }

    @Test
    public void testNextLetterString() {
        for (int i = 0; i < 10; i++) {
            System.out.println(random.nextLetterString());
        }

        for (int i = 1; i < 10; i++) {
            String s = random.nextLetterString(i);
            System.out.println(s);
            Assertions.assertEquals(s.length(), i);
        }
    }

    @Test
    public void testPhrase() {
        for (int i = 0; i < 10; i++) {
            System.out.println(random.nextPhrase());
        }

        for (int i = 100; i < 150; i++) {
            String s = random.nextPhrase(i);
            System.out.println(s);
            Assertions.assertEquals(s.length(), i);
        }
    }

    @Test
    public void testNumericString() {
        for (int i = 0; i < 10; i++) {
            System.out.println(random.nextNumeric());
        }

        for (int i = 1; i < 10; i++) {
            String string = random.nextNumeric(i);
            System.out.println(string);
            Assertions.assertEquals(string.length(), i);
        }
    }

    @Test
    public void testIntBetween() {
        for (int i = 1; i < 10000; i++) {
            int number = random.nextInt(0, i);
            System.out.println(number);
            Assertions.assertTrue(number >= 0 && number < i);
        }
    }

    @Test
    public void testIntBetweenInclusive() {
        for (int i = 1; i < 10000; i++) {
            int number = random.nextIntBetweenInclusive(0, i);
            System.out.println(number);
            Assertions.assertTrue(number >= 0 && number <= i);
        }
    }

    @Test
    public void testLong() {
        for (long i = 1; i < 10000; i++) {
            long number = random.nextLong(0L, i);
            System.out.println(number);
            Assertions.assertTrue(number >= 0 && number < i);
        }
    }

    @Test
    public void testLongInclusive() {
        for (int i = 1; i < 10000; i++) {
            long number = random.nextLongInclusive(0, i);
            System.out.println(number);
            Assertions.assertTrue(number >= 0 && number <= i);
        }
    }

    @Test
    public void testLocation() {
        System.out.println(random.nextLocation());
    }

    @Test
    public void testIsoDatesAndDateTimes() {
        for (int i = 0; i < 100; i++) {
            Assertions.assertTrue(random.nextIsoDate().matches("\\d{4}-\\d{2}-\\d{2}"));
            Assertions.assertTrue(random.nextIsoDateTime().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z?"));
        }
    }

    @Test
    public void testDatesForPatterns() {
        for (int i = 0; i < 100; i++) {
            Assertions.assertTrue(random.nextDateForPattern("dd-MM-yyyy").matches("\\d{2}-\\d{2}-\\d{4}"));
            Assertions.assertTrue(random.nextDateForPattern("yyyy-MM-dd HH:mm:ss").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
            Assertions.assertTrue(random.nextDateForPattern("yyyy-MM-ddTHH:mm").matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}"));
            Assertions.assertTrue(random.nextDateForPattern("dd/MM/yyyy").matches("\\d{2}/\\d{2}/\\d{4}"));
            Assertions.assertTrue(random.nextDateForPattern("HH:mm").matches("\\d{2}:\\d{2}"));
        }
        for (String notPattern : new String[] {"Leo", "rabies shot", "2013-01-01", "MM", "yyyy", "Mad Max", "", "dd-MM-yyyy!"}) {
            Assertions.assertNull(random.nextDateForPattern(notPattern), notPattern);
        }
        Assertions.assertNull(random.nextDateForPattern(null));
    }
}
