package boundary;

import Entity.Dish;
import Entity.Stall;
import control.DishControl;
import control.IVisionService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import javax.swing.*;

class DishPanel extends JPanel {
    private final FoodHubApp app;
    private final DishControl control = DishControl.getInstance();
    private final JTextField employee = new JTextField(8);
    private final JComboBox<Stall> stalls = new JComboBox<>();
    private final DefaultListModel<Dish> model = new DefaultListModel<>();
    private final JList<Dish> list = new JList<>(model);
    private final DishEditor editor;
    private int employeeId;
    private Stall currentStall;
    private Dish currentDish;
    private boolean loading;

    DishPanel(FoodHubApp app, IVisionService vision) {
        super(new BorderLayout(12, 12));
        this.app = app;
        editor = new DishEditor(app, vision);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel selector = new JPanel(new FlowLayout(FlowLayout.LEADING));
        FoodHubApp.label(selector, "Representative employee ID", employee);
        JButton load = new JButton("Load assigned stalls");
        selector.add(load);
        FoodHubApp.label(selector, "Stall", stalls);
        add(selector, BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.getAccessibleContext().setAccessibleName("Dishes at the selected stall");
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(list), editor);
        split.setResizeWeight(0.28);
        split.setDividerLocation(280);
        add(split, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING));
        JButton create = new JButton("New dish");
        JButton save = new JButton("Save dish");
        JButton delete = new JButton("Delete dish");
        actions.add(create); actions.add(delete); actions.add(save);
        add(actions, BorderLayout.SOUTH);
        load.addActionListener(e -> app.perform(() -> {
            int selectedEmployee = Integer.parseInt(employee.getText().trim());
            if (!discardChanges()) return;
            app.runTask("Loading assigned stalls...", () -> control.getStallsForRepresentative(selectedEmployee), found -> {
                employeeId = selectedEmployee;
                loading = true;
                stalls.removeAllItems();
                for (Stall stall : found) stalls.addItem(stall);
                loading = false;
                currentStall = (Stall) stalls.getSelectedItem();
                refresh();
                if (found.isEmpty()) JOptionPane.showMessageDialog(app, "No stalls are assigned to this employee.");
            });
        }));
        stalls.addActionListener(e -> {
            if (loading) return;
            if (!discardChanges()) {
                loading = true; stalls.setSelectedItem(currentStall); loading = false; return;
            }
            currentStall = (Stall) stalls.getSelectedItem();
            refresh();
        });
        list.addListSelectionListener(e -> {
            if (loading || e.getValueIsAdjusting()) return;
            if (!discardChanges()) {
                loading = true; list.setSelectedValue(currentDish, true); loading = false; return;
            }
            currentDish = list.getSelectedValue();
            editor.display(currentDish);
        });
        create.addActionListener(e -> app.perform(() -> {
            if (currentStall == null) throw new IllegalArgumentException("Load a representative's assigned stalls first.");
            if (!discardChanges()) return;
            app.runTask("Loading ingredients...", control::getAllIngredients, ingredients -> {
                editor.setAvailableIngredients(ingredients);
                loading = true; list.clearSelection(); loading = false;
                currentDish = null;
                editor.display(new Dish(0, currentStall.getStallNumber(), "", "", "", 0));
            });
        }));
        save.addActionListener(e -> app.perform(() -> {
            Dish dish = editor.value();
            app.runTask("Saving dish...", () -> dish.getDishId() == 0 ? control.addDish(dish, employeeId) : control.updateDish(dish, employeeId), saved -> refresh());
        }));
        delete.addActionListener(e -> app.perform(() -> {
            Dish dish = editor.value();
            if (dish.getDishId() == 0) throw new IllegalArgumentException("Select a saved dish to delete.");
            if (JOptionPane.showConfirmDialog(app, "Delete " + dish.getName() + "?", "Delete dish", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            app.runTask("Deleting dish...", () -> control.deleteDish(dish.getDishId(), dish.getStallNumber(), employeeId), deleted -> {
                if (!deleted) JOptionPane.showMessageDialog(app, "The dish no longer exists. The list will reload.");
                refresh();
            });
        }));
    }

    boolean discardChanges() {
        return !editor.isDirty() || JOptionPane.showConfirmDialog(app, "Discard unsaved dish changes?", "Unsaved changes",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
    }

    private void refresh() {
        loading = true;
        model.clear(); currentDish = null; editor.display(null);
        loading = false;
        if (currentStall == null) {
            return;
        }
        int stallNumber = currentStall.getStallNumber();
        app.runTask("Loading dishes and ingredients...", () -> new StallData(control.getDishesByStall(stallNumber), control.getAllIngredients()), data -> {
            loading = true;
            model.clear();
            for (Dish dish : data.dishes) model.addElement(dish);
            editor.setAvailableIngredients(data.ingredients);
            currentDish = model.isEmpty() ? null : model.firstElement();
            list.setSelectedValue(currentDish, true);
            editor.display(currentDish);
            loading = false;
        });
    }

    private record StallData(ArrayList<Dish> dishes, ArrayList<Entity.Ingredient> ingredients) {}
}
