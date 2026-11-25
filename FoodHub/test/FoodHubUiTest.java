import boundary.FoodHubApp;
import Entity.Dish;
import control.DishControl;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javax.swing.*;

public class FoodHubUiTest {
    private static FoodHubApp app;
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path database = Files.createTempDirectory("foodhub-ui-").resolve("FoodHub.accdb");
        Files.copy(Path.of(args[0]), database);
        System.setProperty("foodhub.db.path", database.toString());
        try {
            SwingUtilities.invokeAndWait(() -> {
                try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
                catch (Exception e) { throw new RuntimeException(e); }
                app = new FoodHubApp(null); app.setVisible(true);
            });
            edt(() -> {
                check(!button("Detect ingredients").isEnabled(), "Vision interface is disconnected by default");
                field("Representative employee ID").setText("10006");
                button("Load assigned stalls").doClick();
            });
            idle();
            edt(() -> {
                check(!dishList().isSelectionEmpty(), "Load dishes from the assigned stall");
                check(field("Dish name").isEnabled(), "Enable the selected dish editor");
                button("Check allergens").doClick();
            });
            idle();
            edt(() -> {
                JTextArea warnings = find(JTextArea.class, "Allergen declarations");
                check(!warnings.getText().contains("Select Check"), "Show derived allergen declarations");
                button("New dish").doClick();
            });
            idle();
            edt(() -> {
                field("Dish name").setText("UI test dish");
                field("Food type").setText("Pizza");
                field("Price").setText("20.25");
                field("Image path").setText("test-image.png");
                ingredientList().setSelectedIndex(0);
                check(labelText("EDITED"), "Manual ingredient selection sets edited status");
                checkbox().doClick();
                check(labelText("CONFIRMED"), "Confirm the ingredient list");
                button("Save dish").doClick();
            });
            idle();
            ArrayList<Dish> dishes = DishControl.getInstance().getDishesByStall(1);
            Dish saved = dishes.stream().filter(d -> d.getName().equals("UI test dish")).findFirst().orElseThrow();
            check(saved.getIngredients().size() == 1 && saved.getIngredientStatus().equals("CONFIRMED"), "Save form values and ingredient review status");
            edt(() -> {
                dishList().setSelectedValue(saved, true);
                field("Image path").setText("changed-image.png");
                check(ingredientList().isSelectionEmpty() && !checkbox().isSelected(), "Image changes invalidate the prior ingredient list");
                check(labelText("GENERATED"), "Image changes reset the review status");
            });
            edt(() -> {
                respondToDialog("Cancel");
                dishList().setSelectedIndex(0);
                check(field("Dish name").getText().equals("UI test dish"), "Cancel navigation to retain unsaved edits");
            });
            edt(() -> {
                respondToDialog("OK");
                dishList().setSelectedIndex(0);
                check(!field("Dish name").getText().equals("UI test dish"), "Confirm discard before navigating");
            });
            edt(() -> {
                app.setSize(1120, 800); app.validate();
                BufferedImage image = new BufferedImage(app.getWidth(), app.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics(); app.paintAll(graphics); graphics.dispose();
                try { ImageIO.write(image, "png", Path.of(args[1]).toFile()); }
                catch (java.io.IOException e) { throw new RuntimeException(e); }
                check(button("Save dish").isShowing(), "Show the save action");
                app.setSize(900, 650); app.validate();
                check(button("Save dish").getWidth() >= button("Save dish").getPreferredSize().width, "Keep save available at the minimum window size");
            });
            edt(() -> {
                JTabbedPane tabs = all(app, JTabbedPane.class).get(0);
                tabs.setSelectedIndex(1); button("Refresh ingredients").doClick();
            });
            idle();
            edt(() -> {
                JTable table = find(JTable.class, "Ingredient catalog");
                check(table.getRowCount() >= 188, "Load the ingredient management table");
                all(app, JTabbedPane.class).get(0).setSelectedIndex(2);
                button("Refresh catalog").doClick();
            });
            idle();
            edt(() -> check(find(JTable.class, "Allergens and their triggering ingredients").getRowCount() == 14, "Load the allergen catalog table"));
            System.out.println("Passed " + checks + " UI checks. Database copy: " + database);
        } finally {
            if (app != null) edt(() -> app.dispose());
        }
    }

    private static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }

    private static void respondToDialog(String choice) {
        Timer timer = new Timer(100, null);
        timer.addActionListener(e -> {
            for (Window window : Window.getWindows()) {
                if (!(window instanceof JDialog) || !window.isVisible()) continue;
                for (JButton button : all(window, JButton.class)) {
                    if (choice.equals(button.getText())) { timer.stop(); button.doClick(); return; }
                }
            }
        });
        timer.start();
    }

    private static void idle() throws Exception {
        long deadline = System.nanoTime() + 30_000_000_000L;
        while (System.nanoTime() < deadline) {
            boolean[] enabled = {false}; edt(() -> enabled[0] = app.isEnabled());
            if (enabled[0]) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Background task did not complete");
    }

    private static JButton button(String text) {
        return all(app, JButton.class).stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
    }

    private static JTextField field(String text) {
        return all(app, JLabel.class).stream().filter(l -> text.equals(l.getText())).map(l -> (JTextField) l.getLabelFor()).findFirst().orElseThrow();
    }

    private static boolean labelText(String text) { return all(app, JLabel.class).stream().anyMatch(l -> text.equals(l.getText())); }
    private static JCheckBox checkbox() { return all(app, JCheckBox.class).get(0); }
    @SuppressWarnings("unchecked")
    private static JList<Dish> dishList() { return (JList<Dish>) find(JList.class, "Dishes at the selected stall"); }
    private static JList<?> ingredientList() { return all(app, JList.class).stream().filter(l -> l != dishList()).findFirst().orElseThrow(); }

    private static <T extends JComponent> T find(Class<T> type, String accessibleName) {
        return all(app, type).stream().filter(c -> accessibleName.equals(c.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
    }

    private static <T extends Component> ArrayList<T> all(Container root, Class<T> type) {
        ArrayList<T> result = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) result.add(type.cast(child));
            if (child instanceof Container) result.addAll(all((Container) child, type));
        }
        return result;
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }
}
