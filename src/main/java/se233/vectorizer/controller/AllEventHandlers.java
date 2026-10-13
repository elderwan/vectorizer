package se233.vectorizer.controller;

import se233.vectorizer.view.MainView;

public final class  AllEventHandlers {
    /**
     * Prevents creating this event-wiring utility class.
     */
    private AllEventHandlers() {
    }

    /**
     * Connects view events to their matching controller actions.
     *
     * @param controller controller that performs application actions
     * @param view view whose controls receive the event handlers
     */
    public static void install(MainViewController controller, MainView view) {
        view.setOnFilesAccepted(controller::loadFiles);
        installNavigationHandlers(controller, view);
        installActionHandlers(controller, view);
    }

    /**
     * Connects file navigation controls to controller navigation methods.
     *
     * @param controller controller that handles navigation
     * @param view view containing the navigation controls
     */
    private static void installNavigationHandlers(MainViewController controller, MainView view) {
        view.getConfigPane().setOnBack(controller::showPrevious);
        view.getConfigPane().setOnNext(controller::showNext);
    }

    /**
     * Connects export, reset, and settings controls to controller methods.
     *
     * @param controller controller that handles the actions
     * @param view view containing the action controls
     */
    private static void installActionHandlers(MainViewController controller, MainView view) {
        view.getConfigPane().setOnExportAll(controller::exportAll);
        view.getConfigPane().setOnReset(controller::resetAll);
        view.getConfigPane().setOnSettingsChanged(controller::applySettings);
    }
}
