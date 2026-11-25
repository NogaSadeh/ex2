package boundary;

import Entity.Allergen;
import Entity.Ingredient;
import control.AllergenControl;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

class AllergenPanel extends JPanel {
    private final FoodHubApp app;
    private final AllergenControl control = AllergenControl.getInstance();
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{"Code", "Allergen", "Description", "Catalog", "Triggering ingredients"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };

    AllergenPanel(FoodHubApp app) {
        super(new BorderLayout(12, 12));
        this.app = app;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        add(new JLabel("Import the Ministry of Health catalog to update allergen declarations."), BorderLayout.NORTH);
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {130, 180, 300, 70, 350};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.getAccessibleContext().setAccessibleName("Allergens and their triggering ingredients");
        add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING));
        JButton refresh = new JButton("Refresh catalog");
        JButton importXml = new JButton("Import XML catalog");
        actions.add(refresh); actions.add(importXml);
        add(actions, BorderLayout.SOUTH);
        refresh.addActionListener(e -> refresh());
        importXml.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Allergen catalog XML", "xml"));
            if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION) return;
            Path path = chooser.getSelectedFile().toPath();
            app.runTask("Validating XML catalog...", () -> control.parseCatalog(path), catalog -> {
                String message = "Import catalog " + catalog.getCatalogId() + " dated " + catalog.getLastUpdated() + " with " + catalog.getAllergens().size()
                        + " allergens?\nThis replaces this catalog's allergen details and ingredient links.\nAllergens omitted from this catalog will be removed.";
                if (JOptionPane.showConfirmDialog(app, message, "Import catalog", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
                app.runTask("Importing XML catalog...", () -> control.importCatalog(path), count -> {
                    JOptionPane.showMessageDialog(app, "Imported " + count + " allergens.");
                    refresh();
                });
            });
        });
    }

    private void refresh() {
        app.runTask("Loading allergen catalog...", control::getAllergens, allergens -> {
            model.setRowCount(0);
            for (Allergen allergen : allergens) model.addRow(new Object[]{allergen.getAllergenCode(), allergen.getName(),
                    allergen.getDescription(), allergen.getCatalogId(),
                    String.join(", ", allergen.getTriggeringIngredients().stream().map(Ingredient::getIngredientName).toList())});
        });
    }
}
