package control;

import Entity.Ingredient;
import java.util.ArrayList;

public interface IVisionService {
    ArrayList<Ingredient> detectIngredientsFromImage(String imagePath);
}