package se233.vectorizer.controller;

import se233.vectorizer.view.MainView;

public final class AllEventHandlers {
    private AllEventHandlers() {
    }

    public static void install(MainViewController controller, MainView view) {
        view.setOnFilesAccepted(controller::loadFiles);
        view.getConfigPane().setOnBack(controller::showPrevious);
        view.getConfigPane().setOnNext(controller::showNext);
        view.getConfigPane().setOnExportAll(controller::exportAll);
        view.getConfigPane().setOnReset(controller::resetAll);
        view.getConfigPane().setOnSettingsChanged(controller::applySettings);
    }
}
