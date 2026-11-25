package Entity;

import java.util.ArrayList;

public class Dish {
    private int dishId;
    private int stallNumber;
    private String name;
    private String foodType;
    private String imagePath;
    private double price;
    private String ingredientStatus = "GENERATED";
    private ArrayList<Ingredient> ingredients;

    public Dish(int dishId, int stallNumber, String name, String foodType, String imagePath, double price) {
        this.dishId = dishId;
        this.stallNumber = stallNumber;
        this.name = name;
        this.foodType = foodType;
        this.imagePath = imagePath;
        this.price = price;
        this.ingredients = new ArrayList<>();
    }

    public int getDishId() { return dishId; }
    public void setDishId(int dishId) { this.dishId = dishId; }

    public int getStallNumber() { return stallNumber; }
    public void setStallNumber(int stallNumber) { this.stallNumber = stallNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFoodType() { return foodType; }
    public void setFoodType(String foodType) { this.foodType = foodType; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getIngredientStatus() { return ingredientStatus; }
    public void setIngredientStatus(String ingredientStatus) { this.ingredientStatus = ingredientStatus; }

    public ArrayList<Ingredient> getIngredients() { return ingredients; }
    public void setIngredients(ArrayList<Ingredient> ingredients) { this.ingredients = ingredients; }

    @Override
    public String toString() {
        return name + " (" + foodType + ")";
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof Dish && dishId == ((Dish) obj).dishId;
    }

    @Override
    public int hashCode() { return Integer.hashCode(dishId); }
}
