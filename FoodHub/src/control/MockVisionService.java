package control;

import Objects.Ingredient;
import java.util.ArrayList;

public class MockVisionService implements IVisionService {
    @Override
    public ArrayList<Ingredient> detectIngredientsFromImage(String imagePath) {
        // מדמה זיהוי ראשוני מתוך רכיבים קיימים במערכת עבור תמונת המנה
        ArrayList<Ingredient> allIngredients = DishControl.getInstance().getAllIngredients();
        ArrayList<Ingredient> detected = new ArrayList<>();
        
        for (int i = 0; i < Math.min(3, allIngredients.size()); i++) {
            detected.add(allIngredients.get(i));
        }
        return detected;
    }
}