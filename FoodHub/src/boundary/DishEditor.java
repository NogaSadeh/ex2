package boundary;

import Entity.Allergen;
import Entity.Dish;
import Entity.Ingredient;
import control.DishControl;
import control.IVisionService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

class DishEditor extends JPanel {
    private final FoodHubApp app;
    private final JTextField name = new JTextField();
    private final JTextField type = new JTextField();
    private final JTextField image = new JTextField();
    private final JTextField price = new JTextField();
    private final JLabel preview = new JLabel("Select a dish.", SwingConstants.CENTER);
    private final JLabel review = new JLabel("No dish selected");
    private final JCheckBox confirmed = new JCheckBox("I confirm this ingredient list");
    private final DefaultListModel<Ingredient> ingredients = new DefaultListModel<>();
    private final JList<Ingredient> selected = new JList<>(ingredients);
    private final JTextArea warnings = new JTextArea(5, 30);
    private Dish current;
    private String initialState = "";
    private boolean loading;

    DishEditor(FoodHubApp app, IVisionService vision) {
        super(new BorderLayout(8, 12));
        this.app = app;
        JPanel details = new JPanel(new GridLayout(0, 2, 8, 8));
        FoodHubApp.label(details, "Dish name", name);
        FoodHubApp.label(details, "Food type", type);
        FoodHubApp.label(details, "Price", price);
        FoodHubApp.label(details, "Image path", image);
        JPanel imageActions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton browse = new JButton("Choose image");
        JButton detect = new JButton("Detect ingredients");
        imageActions.add(browse); imageActions.add(detect);
        detect.setEnabled(vision != null);
        detect.setToolTipText(vision == null ? "Automatic image detection is unavailable." : "Generate ingredients from the dish image.");
        preview.setPreferredSize(new Dimension(140, 110));
        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(details, BorderLayout.CENTER);
        top.add(preview, BorderLayout.EAST);
        top.add(imageActions, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        selected.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        selected.getAccessibleContext().setAccessibleName("Dish ingredients. Use Control or Command to select several ingredients.");
        JPanel ingredientsPanel = new JPanel(new BorderLayout(8, 8));
        ingredientsPanel.setBorder(BorderFactory.createTitledBorder("Ingredients: select the items in this dish"));
        ingredientsPanel.add(new JScrollPane(selected), BorderLayout.CENTER);
        JPanel reviewPanel = new JPanel(new GridLayout(0, 1));
        reviewPanel.add(new JLabel(vision == null ? "<html>Automatic detection is unavailable. Select ingredients manually.</html>"
                : "<html>Review the detected ingredients, then confirm or edit the list.</html>"));
        reviewPanel.add(review);
        reviewPanel.add(confirmed);
        ingredientsPanel.add(reviewPanel, BorderLayout.SOUTH);
        add(ingredientsPanel, BorderLayout.CENTER);
        warnings.setEditable(false);
        warnings.setLineWrap(true);
        warnings.setWrapStyleWord(true);
        warnings.getAccessibleContext().setAccessibleName("Allergen declarations");
        JPanel warningsPanel = new JPanel(new BorderLayout(8, 8));
        JButton check = new JButton("Check allergens");
        warningsPanel.add(check, BorderLayout.NORTH);
        warningsPanel.add(new JScrollPane(warnings), BorderLayout.CENTER);
        add(warningsPanel, BorderLayout.SOUTH);
        browse.addActionListener(e -> {
            if (current == null) return;
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Dish images", "png", "jpg", "jpeg", "gif"));
            if (chooser.showOpenDialog(app) == JFileChooser.APPROVE_OPTION) {
                image.setText(chooser.getSelectedFile().getAbsolutePath());
                showImage();
            }
        });
        image.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                if (loading || current == null) return;
                selected.clearSelection(); confirmed.setSelected(false); review.setText("GENERATED");
                warnings.setText("Image changed. Review the ingredient list again.");
                preview.setIcon(null); preview.setText("Select image");
            }
            public void insertUpdate(DocumentEvent e) { changed(); }
            public void removeUpdate(DocumentEvent e) { changed(); }
            public void changedUpdate(DocumentEvent e) { changed(); }
        });
        selected.addListSelectionListener(e -> {
            if (loading || e.getValueIsAdjusting() || current == null) return;
            confirmed.setSelected(false); review.setText("EDITED");
            warnings.setText("Ingredients changed. Check allergens again.");
        });
        confirmed.addActionListener(e -> review.setText(confirmed.isSelected() ? "CONFIRMED" : "EDITED"));
        detect.addActionListener(e -> app.perform(() -> {
            if (current == null) throw new IllegalArgumentException("Select or create a dish first.");
            String imagePath = image.getText().trim();
            if (imagePath.isEmpty()) throw new IllegalArgumentException("Choose a dish image first.");
            app.runTask("Detecting ingredients...", () -> vision.detectIngredientsFromImage(imagePath), found -> {
                if (found == null) throw new IllegalStateException("The vision service did not return an ingredient list.");
                for (Ingredient ingredient : found) {
                    if (ingredient == null || !ingredients.contains(ingredient)) throw new IllegalArgumentException("Detected ingredients must exist in the ingredient catalog before use.");
                }
                loading = true;
                selectIngredients(found); confirmed.setSelected(false); review.setText("GENERATED");
                loading = false;
                warnings.setText("Review the detected ingredients, then confirm or edit the list.");
            });
        }));
        check.addActionListener(e -> app.perform(() -> {
            if (current == null) throw new IllegalArgumentException("Select or create a dish first.");
            ArrayList<Ingredient> values = new ArrayList<>(selected.getSelectedValuesList());
            app.runTask("Checking allergen declarations...", () -> DishControl.getInstance().getAllergenWarnings(values), result -> {
                StringBuilder text = new StringBuilder();
                if (!confirmed.isSelected()) text.append("Ingredient list is not confirmed.\n");
                if (result.isEmpty()) text.append("No catalog allergens match the selected ingredients.");
                for (Allergen allergen : result) {
                    text.append(allergen.getName()).append(": ").append(allergen.getDescription()).append("\nIngredients: ");
                    text.append(String.join(", ", allergen.getTriggeringIngredients().stream().map(Ingredient::getIngredientName).toList())).append("\n\n");
                }
                warnings.setText(text.toString()); warnings.setCaretPosition(0);
            });
        }));
        display(null);
    }

    void setAvailableIngredients(ArrayList<Ingredient> available) {
        loading = true;
        ingredients.clear();
        for (Ingredient ingredient : available) ingredients.addElement(ingredient);
        loading = false;
    }

    void display(Dish dish) {
        loading = true;
        current = dish;
        name.setText(dish == null ? "" : dish.getName());
        type.setText(dish == null ? "" : dish.getFoodType());
        price.setText(dish == null ? "" : Double.toString(dish.getPrice()));
        image.setText(dish == null ? "" : dish.getImagePath());
        selectIngredients(dish == null ? new ArrayList<>() : dish.getIngredients());
        review.setText(dish == null ? "No dish selected" : dish.getIngredientStatus());
        confirmed.setSelected(dish != null && "CONFIRMED".equals(dish.getIngredientStatus()));
        for (JComponent field : new JComponent[]{name, type, price, image, selected, confirmed}) field.setEnabled(dish != null);
        warnings.setText(dish == null ? "Select a dish or create a new dish." : "Select Check allergens to view declarations for this ingredient list.");
        loading = false;
        showImage();
        initialState = state();
    }

    private void selectIngredients(ArrayList<Ingredient> values) {
        selected.clearSelection();
        for (int i = 0; i < ingredients.size(); i++) if (values.contains(ingredients.get(i))) selected.addSelectionInterval(i, i);
    }

    private void showImage() {
        preview.setIcon(null);
        try {
            Path path = Path.of(image.getText());
            if (!image.getText().isBlank() && Files.isRegularFile(path)) {
                ImageIcon icon = new ImageIcon(path.toString());
                if (icon.getIconWidth() > 0) {
                    int width = icon.getIconWidth(); int height = icon.getIconHeight();
                    double scale = Math.min(140.0 / width, 110.0 / height);
                    preview.setIcon(new ImageIcon(icon.getImage().getScaledInstance(Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)), Image.SCALE_SMOOTH)));
                    preview.setText(""); return;
                }
            }
        } catch (java.nio.file.InvalidPathException e) { preview.setToolTipText("The image path is invalid."); }
        preview.setText(current == null ? "Select a dish." : "Image unavailable");
    }

    Dish value() {
        if (current == null) throw new IllegalArgumentException("Select or create a dish first.");
        Dish dish = new Dish(current.getDishId(), current.getStallNumber(), name.getText(), type.getText(), image.getText(), Double.parseDouble(price.getText().trim()));
        dish.setIngredients(new ArrayList<>(selected.getSelectedValuesList()));
        dish.setIngredientStatus(review.getText());
        return dish;
    }

    boolean isDirty() { return current != null && !initialState.equals(state()); }

    private String state() {
        return name.getText() + '\u0000' + type.getText() + '\u0000' + price.getText() + '\u0000' + image.getText()
                + '\u0000' + selected.getSelectedValuesList().stream().map(Ingredient::getIngredientId).toList() + '\u0000' + review.getText();
    }
}
