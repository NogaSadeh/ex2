package boundary;

import Entity.Ingredient;
import control.DishControl;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

class IngredientPanel extends JPanel {
    private final FoodHubApp app;
    private final DishControl control = DishControl.getInstance();
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{"ID", "Ingredient name"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);

    IngredientPanel(FoodHubApp app) {
        super(new BorderLayout(12, 12));
        this.app = app;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        add(new JLabel("Manage the ingredient catalog. Linked ingredients cannot be deleted."), BorderLayout.NORTH);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getAccessibleContext().setAccessibleName("Ingredient catalog");
        add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING));
        JButton refresh = new JButton("Refresh ingredients");
        JButton create = new JButton("New ingredient");
        JButton edit = new JButton("Rename ingredient");
        JButton delete = new JButton("Delete ingredient");
        actions.add(refresh); actions.add(create); actions.add(edit); actions.add(delete);
        add(actions, BorderLayout.SOUTH);
        refresh.addActionListener(e -> refresh());
        create.addActionListener(e -> edit(new Ingredient(0, "")));
        edit.addActionListener(e -> app.perform(() -> edit(selectedIngredient())));
        delete.addActionListener(e -> app.perform(() -> {
            Ingredient ingredient = selectedIngredient();
            if (JOptionPane.showConfirmDialog(app, "Delete " + ingredient.getIngredientName() + "?", "Delete ingredient", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            app.runTask("Deleting ingredient...", () -> control.deleteIngredient(ingredient.getIngredientId()), deleted -> refresh());
        }));
    }

    private Ingredient selectedIngredient() {
        int row = table.getSelectedRow();
        if (row < 0) throw new IllegalArgumentException("Select an ingredient first.");
        row = table.convertRowIndexToModel(row);
        return new Ingredient((Integer) model.getValueAt(row, 0), (String) model.getValueAt(row, 1));
    }

    private void edit(Ingredient ingredient) {
        String name = JOptionPane.showInputDialog(app, "Ingredient name", ingredient.getIngredientName());
        if (name == null) return;
        ingredient.setIngredientName(name);
        app.runTask("Saving ingredient...", () -> { control.saveIngredient(ingredient); return ingredient; }, saved -> refresh());
    }

    private void refresh() {
        app.runTask("Loading ingredients...", control::getAllIngredients, ingredients -> {
            model.setRowCount(0);
            for (Ingredient ingredient : ingredients) model.addRow(new Object[]{ingredient.getIngredientId(), ingredient.getIngredientName()});
        });
    }
}
