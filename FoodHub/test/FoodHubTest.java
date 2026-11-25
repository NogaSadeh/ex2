import Entity.Allergen;
import Entity.Dish;
import Entity.Ingredient;
import control.AllergenControl;
import control.Consts;
import control.DishControl;
import java.nio.file.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FoodHubTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args[0].equals("--path")) {
            check(Path.of(Consts.DB_FILE_PATH).toRealPath().equals(Path.of(args[1]).toRealPath()), "Resolve the database path");
            System.out.println("Database path check passed.");
            return;
        }
        Path database = Files.createTempDirectory("foodhub-test-").resolve("FoodHub.accdb");
        Files.copy(Path.of(args[0]), database);
        System.setProperty("foodhub.db.path", database.toString());
        DishControl dishes = DishControl.getInstance();
        check(Path.of(Consts.DB_FILE_PATH).equals(database), "Use the selected database");
        check(dishes.getAllStalls().get(1).getX() == 16.5, "Read actual coordinate fields without truncation");
        check(dishes.getStallsForRepresentative(10006).stream().anyMatch(s -> s.getStallNumber() == 1), "Read assigned stalls");
        check(dishes.getStallsForRepresentative(999999).isEmpty(), "Unknown staff has no stalls");

        Dish dish = new Dish(0, 1, "Test dish", "Pizza", "assets/menu/pizza.png", 12.50);
        dish.setIngredientStatus("CONFIRMED");
        dish.setIngredients(new ArrayList<>(List.of(new Ingredient(1, "Wheat Flour"), new Ingredient(2, "Whole Wheat Bread"))));
        check(dishes.addDish(dish, 10006), "Insert a dish with a manual database key");
        check(dish.getDishId() > 341, "Return the persisted key");
        check(count("SELECT COUNT(*) FROM DishIngredient WHERE DishID=" + dish.getDishId()) == 2, "Save ingredient links");
        check(!dishes.getAllergenWarningsForDish(dish.getDishId()).isEmpty(), "Derive warnings from ingredients");
        dish.setName("Updated test dish");
        dish.setIngredients(new ArrayList<>(List.of(new Ingredient(1, "Wheat Flour"))));
        dish.setIngredientStatus("EDITED");
        check(dishes.updateDish(dish, 10006), "Update own stall dish");
        check(count("SELECT COUNT(*) FROM DishIngredient WHERE DishID=" + dish.getDishId()) == 1, "Replace ingredient links");
        expectFailure(() -> dishes.updateDish(dish, 999999), "Reject another representative");
        Dish wrongStall = new Dish(dish.getDishId(), 2, "Wrong stall", "Pizza", "image.png", 1);
        expectFailure(() -> dishes.updateDish(wrongStall, 10006), "Reject changing a dish through another stall");
        dish.setIngredients(new ArrayList<>(List.of(new Ingredient(999999, "Unknown"))));
        expectFailure(() -> dishes.updateDish(dish, 10006), "Reject missing ingredients");
        check(count("SELECT COUNT(*) FROM DishIngredient WHERE DishID=" + dish.getDishId()) == 1, "Rollback failed update");
        Dish invalid = new Dish(0, 1, "", "Pizza", "image.png", 1);
        expectFailure(() -> dishes.addDish(invalid, 10006), "Reject blank dish names");
        invalid.setName("Invalid price"); invalid.setPrice(Double.NaN);
        expectFailure(() -> dishes.addDish(invalid, 10006), "Reject non-finite prices");
        invalid.setPrice(1); invalid.setIngredientStatus(null);
        expectFailure(() -> dishes.addDish(invalid, 10006), "Reject missing review status");
        Dish failedInsert = new Dish(0, 1, "Failed insert", "Pizza", "image.png", 1);
        failedInsert.setIngredients(new ArrayList<>(List.of(new Ingredient(999999, "Unknown"))));
        expectFailure(() -> dishes.addDish(failedInsert, 10006), "Rollback an insert with missing ingredients");
        check(failedInsert.getDishId() == 0 && count("SELECT COUNT(*) FROM Dish WHERE DishName='Failed insert'") == 0, "Failed insert leaves no dish or assigned ID");
        check(dishes.deleteDish(dish.getDishId(), 1, 10006), "Delete an unused dish");
        check(count("SELECT COUNT(*) FROM DishIngredient WHERE DishID=" + dish.getDishId()) == 0, "Delete ingredient links");
        expectFailure(() -> dishes.deleteDish(1, 1, 10006), "Keep dishes referenced by orders");
        check(count("SELECT COUNT(*) FROM DishIngredient WHERE DishID=1") > 0, "Rollback links after failed delete");

        Ingredient ingredient = new Ingredient(0, "Test ingredient");
        dishes.saveIngredient(ingredient);
        check(ingredient.getIngredientId() > 188, "Create an ingredient");
        ingredient.setIngredientName("Renamed ingredient"); dishes.saveIngredient(ingredient);
        check(dishes.getAllIngredients().contains(ingredient), "Read saved ingredient");
        check(dishes.deleteIngredient(ingredient.getIngredientId()), "Delete an unused ingredient");
        expectFailure(() -> dishes.deleteIngredient(1), "Keep linked ingredients");

        AllergenControl allergens = AllergenControl.getInstance();
        check(allergens.getAllergens().size() == 14, "Read the complete allergen catalog");
        check(allergens.importCatalog(Path.of(args[1])) == 2, "Import XML catalog");
        check(count("SELECT COUNT(*) FROM Ingredient WHERE IngredientID=900001") == 1, "Import missing ingredients");
        check(allergens.importCatalog(Path.of(args[1])) == 2, "Repeat import without duplicates");
        ArrayList<Allergen> warnings = dishes.getAllergenWarnings(new ArrayList<>(List.of(new Ingredient(900001, "Test wheat"))));
        check(warnings.size() == 1 && warnings.get(0).getAllergenCode().equals("TEST_GLUTEN"), "Use imported allergen links");
        check(warnings.get(0).getTriggeringIngredients().size() == 1, "Explain which ingredient triggers a warning");
        Path xml = database.resolveSibling("invalid.xml");
        Files.writeString(xml, "<!DOCTYPE x [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><allergenCatalog id='90' lastUpdated='2026-11-25'>&secret;</allergenCatalog>");
        expectFailure(() -> allergens.importCatalog(xml), "Reject external entities");
        Files.writeString(xml, "<allergenCatalog id='90' lastUpdated='2026-11-25'><allergen code='DUP' name='One' description='One'/><allergen code='dup' name='Two' description='Two'/></allergenCatalog>");
        expectFailure(() -> allergens.importCatalog(xml), "Reject duplicate allergen codes");
        check(count("SELECT COUNT(*) FROM Allergen WHERE AllergenCode='TEST_GLUTEN'") == 1, "Invalid XML leaves data unchanged");
        Files.writeString(xml, "<allergenCatalog id='90' lastUpdated='2026-11-24'><allergen code='TEST_GLUTEN' name='Old' description='Old'/></allergenCatalog>");
        expectFailure(() -> allergens.importCatalog(xml), "Reject stale catalog");
        check(count("SELECT COUNT(*) FROM Allergen WHERE AllergenCode='TEST_NUTS'") == 1, "Stale import leaves links unchanged");
        Files.writeString(xml, "<allergenCatalog id='91' lastUpdated='2026-11-24'><allergen code='TEST_NEW' name='New' description='New'/><allergen code='TEST_GLUTEN' name='Old' description='Old'/></allergenCatalog>");
        expectFailure(() -> allergens.importCatalog(xml), "Rollback an import that fails after its first allergen");
        check(count("SELECT COUNT(*) FROM Allergen WHERE AllergenCode='TEST_NEW'") == 0, "Rollback the first imported allergen");
        check(count("SELECT COUNT(*) FROM AllergenCatalog WHERE CatalogID=91") == 0, "Rollback catalog metadata");
        Files.writeString(xml, "<allergenCatalog id='90' lastUpdated='2026-11-26'><allergen code='TEST_GLUTEN' name='Updated gluten' description='New description'><ingredient id='900002' name='Test almonds'/></allergen></allergenCatalog>");
        check(allergens.importCatalog(xml) == 1, "Import a replacement snapshot");
        check(count("SELECT COUNT(*) FROM Allergen WHERE AllergenCode='TEST_NUTS'") == 0, "Remove omitted allergens from the same catalog");
        check(count("SELECT COUNT(*) FROM IngredientAllergen WHERE IngredientID=900001") == 0, "Remove obsolete links");
        check(count("SELECT COUNT(*) FROM Allergen WHERE CatalogID=1") == 14, "Keep other catalogs");
        check(dishes.getAllergenWarnings(new ArrayList<>(List.of(new Ingredient(900001, "Test wheat")))).isEmpty(), "Warnings use replacement links");
        check(dishes.getAllergenWarnings(new ArrayList<>(List.of(new Ingredient(900002, "Test almonds")))).size() == 1, "Warnings use the new triggering ingredient");
        Files.writeString(xml, "<allergenCatalog id='92' lastUpdated='2026-11-25'><allergen code='BAD' name='Bad' description='Bad'><ingredient id='0' name='Bad'/></allergen></allergenCatalog>");
        expectFailure(() -> allergens.importCatalog(xml), "Reject invalid ingredient IDs");
        Files.writeString(xml, "<allergenCatalog id='92' lastUpdated='2026-11-25'><allergen code='BAD' name='Bad' description='Bad'><ingredient id='1' name='One'/><ingredient id='1' name='One'/></allergen></allergenCatalog>");
        expectFailure(() -> allergens.importCatalog(xml), "Reject duplicate ingredient links");
        Files.writeString(xml, "<allergenCatalog id='92' lastUpdated='2026-11-25'><allergen code='BAD' name='Bad' description='Bad'><ingredient id='1' name='One'/></allergen><allergen code='OTHER' name='Other' description='Other'><ingredient id='1' name='Two'/></allergen></allergenCatalog>");
        expectFailure(() -> allergens.importCatalog(xml), "Reject conflicting ingredient names");
        System.setProperty("foodhub.catalog.maxBytes", "10");
        expectFailure(() -> allergens.importCatalog(xml), "Reject oversized XML before parsing");
        System.clearProperty("foodhub.catalog.maxBytes");
        System.out.println("Passed " + checks + " checks. Database copy: " + database);
    }

    private static int count(String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(Consts.CONN_STR); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next(); return rs.getInt(1);
        }
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }

    private static void expectFailure(Action action, String message) throws Exception {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException e) { checks++; return; }
        throw new AssertionError(message);
    }

    private interface Action { void run() throws Exception; }
}
