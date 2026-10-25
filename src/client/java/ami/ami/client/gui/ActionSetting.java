package ami.ami.client.gui;

/** Button-like row that runs an action (for example resetting telemetry). */
public final class ActionSetting extends Setting {
    private final String buttonLabel;
    private final Runnable action;

    public ActionSetting(String label, String description, String buttonLabel, Runnable action) {
        super(label, description);
        this.buttonLabel = buttonLabel;
        this.action = action;
    }

    public String buttonLabel() {
        return buttonLabel;
    }

    public void run() {
        action.run();
    }

    @Override
    public int rowHeight() {
        return 24;
    }
}
