package se233.vectorizer;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        FileValidatorTest.class,
        ZipHandlerTest.class,
        ColorAnalyzerTest.class,
        VectorizationServiceTest.class,
        BatchProcessorTest.class,
        ConversionSettingsTest.class
})
public class JUnitTestSuite {
}
