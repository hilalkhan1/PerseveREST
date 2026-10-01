package io.resttestgen.implementation.parametervalueprovider.single;

// Modified for PerseveREST (REST League 2027): realistic ranges for unbounded numbers, and dates in the standard
// format of OpenAPI. See NOTICE.

import com.mifmif.common.regex.Generex;
import io.resttestgen.core.Environment;
import io.resttestgen.core.datatype.parameter.attributes.ParameterTypeFormat;
import io.resttestgen.core.datatype.parameter.leaves.*;
import io.resttestgen.core.helper.ExtendedRandom;
import io.resttestgen.core.testing.parametervalueprovider.ParameterValueProvider;
import kotlin.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Generates a random value for the given parameter.
 */
public class RandomParameterValueProvider extends ParameterValueProvider {

    private static final Logger logger = LogManager.getLogger(RandomParameterValueProvider.class);

    private static final ExtendedRandom random = Environment.getInstance().getRandom();

    // When the specification leaves a number unbounded, a uniform choice over the whole representable range
    // almost always gives a huge value (e.g., a Kafka topic with a billion partitions), which APIs reject or
    // which exhausts them. So most of the time the missing bound is replaced by one at this distance from the
    // other bound (or the range starts at zero), and the whole range is used only occasionally.
    private static final int REALISTIC_RANGE_PERCENTAGE = 80;
    private static final long REALISTIC_RANGE_SIZE = 100;

    // How often dates and date-times are generated in the standard format of OpenAPI (RFC 3339)
    private static final int STANDARD_DATE_PERCENTAGE = 85;

    @Override
    public Pair<ParameterValueProvider, Object> provideValueFor(LeafParameter leafParameter) {
        return new Pair<>(this, generateValueFor(leafParameter));
    }

    private Object generateValueFor(LeafParameter leafParameter) {

        if (leafParameter instanceof StringParameter) {
            return generateCompliantString((StringParameter) leafParameter);
        } else if (leafParameter instanceof NumberParameter) {
            return generateCompliantNumber((NumberParameter) leafParameter);
        } else if (leafParameter instanceof BooleanParameter) {
            return random.nextBoolean();
        } else if (leafParameter instanceof NullParameter) {
            return null;
        } else {
            switch (random.nextInt(0, 5)) {
                case 0:
                    return random.nextString();
                case 1:
                    return random.nextInt();
                case 2:
                    return random.nextDoubl(-100000., 100000.);
                case 3:
                    return random.nextBoolean();
                default:
                    return null;
            }
        }
    }

    private String generateCompliantString(StringParameter parameter) {

        // If pattern (regex) is provided for the string, use it with 80% probability
        if (parameter.getPattern() != null && !parameter.getPattern().isEmpty() && random.nextInt(10) < 8) {

            String pattern = parameter.getPattern();

            // Clean pattern if it starts with ^ and ends with $
            if (pattern.startsWith("^") && pattern.endsWith("$")) {
                pattern = pattern.substring(1, pattern.length() - 1);
            }

            // Compute values of minLength and maxLength in the case they are null
            int min = parameter.getMinLength() == null || parameter.getMinLength() < 0 ? 0 : parameter.getMinLength();
            int max = parameter.getMaxLength() == null || parameter.getMaxLength() < min ? min + random.nextInt(20) : parameter.getMaxLength();


            try {
                Generex generex = new Generex(pattern);
                String generated = generex.random(min, max);
                logger.debug("Generated this string from regex: {}", generated);
                return generated;
            }

            // If the pattern is invalid, ignore it and continue with standard random generation
            catch (IllegalArgumentException e) {
                logger.warn("The specified pattern ({}) for parameter {} is invalid. Ignoring it.", parameter.getPattern(), parameter);
            }

            // Catch stack overflows
            catch (StackOverflowError e) {
                logger.warn("Generating a value for pattern {} cause a StackOverflowError. Generating now a random string.", pattern);
            }
        }

        // Generate a random length according to the provided bounds
        int length = random.nextLength(parameter.getMinLength(), parameter.getMaxLength());

        // Generate a random string in multiple format
        String generatedString = random.nextString(length);

        // Replace the generated string with the actual correct format in 90% of the cases
        if (random.nextInt(10) < 9) {
            switch (parameter.inferFormat()) {
                case BYTE:
                    generatedString = random.nextBase64();
                    break;
                case BINARY:
                    generatedString = random.nextBinaryString();
                    break;
                // OpenAPI's date formats are RFC 3339 (2014-05-21, 2014-05-21T08:30:00Z): APIs usually reject other
                // formats before reaching their logic, so these are used only sometimes
                case DATE:
                    generatedString = random.nextInt(100) < STANDARD_DATE_PERCENTAGE ? random.nextIsoDate() : random.nextDate();
                    break;
                case DATE_TIME:
                    generatedString = random.nextInt(100) < STANDARD_DATE_PERCENTAGE ? random.nextIsoDateTime() : random.nextDateTime();
                    break;
                case TIME:
                    generatedString = random.nextTime();
                    break;
                case DURATION:
                    generatedString = random.nextTimeDuration();
                    break;
                case PASSWORD:
                    generatedString = random.nextRandomString(length);
                    break;
                case HOSTNAME:
                    generatedString = random.nextDomain(true);
                    break;
                case URI:
                    generatedString = random.nextURI();
                    break;
                case UUID:
                    generatedString = random.nextUUID();
                    break;
                case IPV4:
                    generatedString = random.nextIPV4();
                    break;
                case IPV6:
                    generatedString = random.nextIPV6();
                    break;
                case EMAIL:
                    generatedString = random.nextEmail();
                    break;
                case PHONE:
                    generatedString = random.nextPhoneNumber();
                    break;
                case IBAN:
                    generatedString = random.nextIBAN();
                    break;
                case SSN:
                    generatedString = random.nextSSN();
                    break;
                case FISCAL_CODE:
                    // TODO Add Fiscal Code
                    generatedString = random.nextString(length);
                    break;
                case LOCATION:
                    generatedString = random.nextLocation();
                    break;
                default:
                    generatedString = random.nextString(length);
            }
        }

        return generatedString;
    }

    private Number generateCompliantNumber(NumberParameter parameter) {

        // Get the actual format, or infer it
        ParameterTypeFormat format = parameter.getOrInferFormat();

        // If the parameter is a double
        if (format == ParameterTypeFormat.DOUBLE) {

            // Set min and max value, if defined
            double min = parameter.getMinimum() != null ? parameter.getMinimum() : -Double.MAX_VALUE;
            double max = parameter.getMaximum() != null ? parameter.getMaximum() : Double.MAX_VALUE;

            // Exclude values if minimum or maximum are exclusive
            min = parameter.isExclusiveMinimum() ? min + Double.MIN_VALUE : min;
            max = parameter.isExclusiveMaximum() ? max - Double.MIN_VALUE : max;

            if (useRealisticRange(parameter)) {
                double[] range = realisticRange(parameter, min, max);
                min = range[0];
                max = range[1];
            }

            // If min is not less than max, reset one of the two variables randomly
            if (min > max) {
                if (random.nextBoolean()) {
                    min = -Double.MAX_VALUE;
                } else {
                    max = Double.MAX_VALUE;
                }
            }

            // If min and max are still the same, just use that value
            if (min == max) {
                return min;
            }

            // Generate and return the value
            return random.nextDoubl(min, max);
        }

        // If the parameter is a float
        else if (format == ParameterTypeFormat.FLOAT) {

            // Set min and max value, if defined
            float min = parameter.getMinimum() != null ? parameter.getMinimum().floatValue() : -Float.MAX_VALUE;
            float max = parameter.getMaximum() != null ? parameter.getMaximum().floatValue() : Float.MAX_VALUE;

            // Exclude values if minimum or maximum are exclusive
            min = parameter.isExclusiveMinimum() ? min + Float.MIN_VALUE : min;
            max = parameter.isExclusiveMaximum() ? max - Float.MIN_VALUE : max;

            if (useRealisticRange(parameter)) {
                double[] range = realisticRange(parameter, min, max);
                min = (float) range[0];
                max = (float) range[1];
            }

            // If min is not less than max, reset one of the two variables randomly
            if (min >= max) {
                if (random.nextBoolean()) {
                    min = -Float.MAX_VALUE;
                } else {
                    max = Float.MAX_VALUE;
                }
            }

            // If min and max are still the same, just use that value
            if (min == max) {
                return min;
            }

            return random.nextFloa(min, max);
        }

        // If the parameter is an integer or long
        else {

            // Is the parameter a long or an integer?
            boolean isLong = format == ParameterTypeFormat.INT64 || format == ParameterTypeFormat.UINT64;

            // Default
            long min = (long) parameter.getMinimumRepresentableValue();
            long max = (long) parameter.getMaximumRepresentableValue();

            // Set min and max value, if defined
            min = parameter.getMinimum() != null ? parameter.getMinimum().longValue() : min;
            max = parameter.getMaximum() != null ? parameter.getMaximum().longValue() : max;

            // Exclude values if minimum or maximum are exclusive
            min = parameter.isExclusiveMinimum() ? min + 1 : min;
            max = parameter.isExclusiveMaximum() ? max - 1 : max;

            if (useRealisticRange(parameter)) {
                double[] range = realisticRange(parameter, min, max);
                min = (long) range[0];
                max = (long) range[1];
            }

            // If min is not less than max, reset one of the two variables randomly
            if (min >= max) {
                if (random.nextBoolean()) {
                    min = Long.MIN_VALUE;
                } else {
                    max = Long.MAX_VALUE;
                }
            }

            if (isLong) {
                // If min and max are still the same, just use that value
                if (min == max) {
                    return min;
                }
                return random.nextLong(min, max);
            } else {
                // If min and max are still the same, just use that value
                if (min == max) {
                    return (int) min;
                }
                return random.nextInt((int) min, (int) max);
            }
        }
    }

    /**
     * Whether to narrow the range of a number that the specification does not bound on both sides.
     */
    private boolean useRealisticRange(NumberParameter parameter) {
        return (parameter.getMinimum() == null || parameter.getMaximum() == null)
                && random.nextInt(100) < REALISTIC_RANGE_PERCENTAGE;
    }

    /**
     * Replaces the missing bounds with realistic ones: [0, 100] if the specification gives no bound, otherwise
     * a range of 100 next to the bound it gives. The result always stays within [min, max].
     */
    private double[] realisticRange(NumberParameter parameter, double min, double max) {
        if (parameter.getMinimum() == null && parameter.getMaximum() == null) {
            return new double[]{Math.max(0, min), Math.min(REALISTIC_RANGE_SIZE, max)};
        } else if (parameter.getMinimum() != null) {
            return new double[]{min, Math.min(min + REALISTIC_RANGE_SIZE, max)};
        } else {
            return new double[]{Math.max(max - REALISTIC_RANGE_SIZE, min), max};
        }
    }
}
