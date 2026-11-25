package Objects;

import java.util.ArrayList;

public class Allergen {
    private String allergenCode;
    private String name;
    private String description;
    private int catalogId;
    private ArrayList<Ingredient> triggeringIngredients;

    public Allergen(String allergenCode, String name, String description, int catalogId) {
        this.allergenCode = allergenCode;
        this.name = name;
        this.description = description;
        this.catalogId = catalogId;
        this.triggeringIngredients = new ArrayList<>();
    }

    public String getAllergenCode() { return allergenCode; }
    public void setAllergenCode(String allergenCode) { this.allergenCode = allergenCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getCatalogId() { return catalogId; }
    public void setCatalogId(int catalogId) { this.catalogId = catalogId; }

    public ArrayList<Ingredient> getTriggeringIngredients() { return triggeringIngredients; }
    public void setTriggeringIngredients(ArrayList<Ingredient> list) { this.triggeringIngredients = list; }

    @Override
    public String toString() {
        return allergenCode + " - " + name;
    }
}