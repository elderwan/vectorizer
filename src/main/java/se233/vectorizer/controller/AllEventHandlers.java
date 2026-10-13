package se233.vectorizer.controller;

import se233.vectorizer.view.MainView;

public final class AllEventHandlers {
    private AllEventHandlers() {
    }

    public static void install(MainViewController controller, MainView view) {
        view.setOnFilesAccepted(controller::loadFiles);
        installNavigationHandlers(controller, view);
        installActionHandlers(controller, view);
    }

    private static void installNavigationHandlers(MainViewController controller, MainView view) {
        view.getConfigPane().setOnBack(controller::showPrevious);
        view.getConfigPane().setOnNext(controller::showNext);
    }

    private static void installActionHandlers(MainViewController controller, MainView view) {
        view.getConfigPane().setOnExportAll(controller::exportAll);
        view.getConfigPane().setOnReset(controller::resetAll);
        view.getConfigPane().setOnSettingsChanged(controller::applySettings);
    }
}
