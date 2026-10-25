package ami.ami.client.gui;

/** A single control shown inside an expanded module card. */
public abstract class Setting {
    private final String label;
    private final String description;

    protected Setting(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** Height of this row inside the module card. */
    public abstract int rowHeight();
}
