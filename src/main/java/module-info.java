module se233.vectorizer {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires java.desktop;

    exports se233.vectorizer;
    exports se233.vectorizer.controller;
    exports se233.vectorizer.model;
    exports se233.vectorizer.util;
    exports se233.vectorizer.view;
    exports se233.vectorizer.exception;

    opens se233.vectorizer.controller to javafx.fxml;
    opens se233.vectorizer.view to javafx.fxml;
}
