package se233.vectorizer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Small Java process used to test ProcessBuilder integration without requiring
 * Potrace on the test machine.
 */
public final class FakePotrace {
    private FakePotrace() {
    }

    public static void main(String[] args) throws Exception {
        String color = "#000000";
        for (int index = 0; index < args.length - 1; index++) {
            if ("-C".equals(args[index])) {
                color = args[index + 1];
            }
            if ("-o".equals(args[index])) {
                Files.writeString(Path.of(args[index + 1]),
                        "<svg xmlns=\"http://www.w3.org/2000/svg\">"
                                + "<g transform=\"scale(1)\"><path fill=\"" + color
                                + "\" d=\"M0 0 L1 0 L1 1 Z\"/></g></svg>",
                        StandardCharsets.UTF_8);
                return;
            }
        }
        System.exit(2);
    }
}
