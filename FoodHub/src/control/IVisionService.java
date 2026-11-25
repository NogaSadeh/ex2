package control;

import Objects.Ingredient;
import java.util.ArrayList;

public interface IVisionService {
    ArrayList<Ingredient> detectIngredientsFromImage(String imagePath);
}