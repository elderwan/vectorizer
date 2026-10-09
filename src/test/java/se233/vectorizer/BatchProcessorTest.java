package se233.vectorizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import se233.vectorizer.controller.BatchProcessor;
import se233.vectorizer.model.ConversionSettings;
import se233.vectorizer.model.ImageItem;
import se233.vectorizer.model.VectorizationService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class BatchProcessorTest {
    @TempDir
    Path tempDirectory;

    @Test
    void usesParallelModeForRequiredTrigger() throws Exception {
        VectorizationService service = Mockito.mock(VectorizationService.class);
        Path source = Files.writeString(tempDirectory.resolve("source.svg"), "<svg/>");
        ImageItem first = item("first.png");
        ImageItem second = item("second.png");
        when(service.convertAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(source));

        ConversionSettings settings = new ConversionSettings();
        BatchProcessor.BatchResult result = new BatchProcessor(service)
                .processAsync(List.of(first, second), settings, tempDirectory.resolve("out"), null)
                .join();

        assertTrue(result.parallel());
        assertEquals(2, result.successes().size());
        Mockito.verify(service, Mockito.times(2)).convertAsync(any(), any());
    }

    @Test
    void usesSequentialModeWhenDetailIsNotMedium() {
        ConversionSettings settings = new ConversionSettings();
        settings.setDetailLevel(ConversionSettings.DetailLevel.HIGH);
        assertTrue(!BatchProcessor.shouldProcessInParallel(
                List.of(Mockito.mock(ImageItem.class), Mockito.mock(ImageItem.class)), settings));
    }

    private ImageItem item(String name) {
        ImageItem item = Mockito.mock(ImageItem.class);
        when(item.getFile()).thenReturn(tempDirectory.resolve(name).toFile());
        return item;
    }
}
