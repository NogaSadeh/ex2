package boundary;

import control.IVisionService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.*;

public class FoodHubApp extends JFrame {
    private final JLabel status = new JLabel("Select a representative to manage dishes.");
    private final DishPanel dishes;

    public FoodHubApp(IVisionService visionService) {
        super("FoodHub Market");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        dishes = new DishPanel(this, visionService);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Dishes", dishes);
        tabs.addTab("Ingredients", new IngredientPanel(this));
        tabs.addTab("Allergen catalog", new AllergenPanel(this));
        add(tabs, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        add(status, BorderLayout.SOUTH);
        setMinimumSize(new Dimension(900, 650));
        setSize(1120, 800);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (dishes.discardChanges()) dispose();
            }
        });
    }

    <T> void runTask(String message, Callable<T> task, Consumer<T> finished) {
        setEnabled(false);
        status.setText(message);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception { return task.call(); }

            @Override
            protected void done() {
                setEnabled(true);
                try {
                    T result = get();
                    status.setText("Ready.");
                    finished.accept(result);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError(e);
                } catch (ExecutionException e) {
                    showError(e.getCause());
                } catch (RuntimeException e) {
                    showError(e);
                }
            }
        }.execute();
    }

    void perform(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException e) { showError(e); }
    }

    void showError(Throwable error) {
        status.setText("Action failed. Check the error and try again.");
        JOptionPane.showMessageDialog(this, error.getMessage(), "FoodHub error", JOptionPane.ERROR_MESSAGE);
    }

    static void label(JPanel form, String text, JComponent field) {
        JLabel label = new JLabel(text);
        label.setLabelFor(field);
        form.add(label);
        form.add(field);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) { System.err.println(e.getMessage()); }
            new FoodHubApp(null).setVisible(true);
        });
    }
}
