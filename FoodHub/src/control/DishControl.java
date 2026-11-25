package control;

import Entity.Allergen;
import Entity.Dish;
import Entity.Ingredient;
import Entity.Stall;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DishControl {
    private static final DishControl instance = new DishControl();
    public static final String SQL_SEL_STALLS =
            "SELECT StallNumber, StallName, FloorNumber, XCoord, YCoord FROM Stall ORDER BY StallNumber";
    public static final String SQL_SEL_DISHES_BY_STALL =
            "SELECT DishID, StallNumber, DishName, FoodType, ImagePath, Price, IngredientStatus FROM Dish WHERE StallNumber = ? ORDER BY DishName";
    public static final String SQL_SEL_INGREDIENTS_BY_DISH =
            "SELECT i.IngredientID, i.IngredientName FROM Ingredient i " +
            "INNER JOIN DishIngredient di ON i.IngredientID = di.IngredientID WHERE di.DishID = ? ORDER BY i.IngredientName";
    public static final String SQL_SEL_ALL_INGREDIENTS =
            "SELECT IngredientID, IngredientName FROM Ingredient ORDER BY IngredientName";
    public static final String SQL_INS_DISH =
            "INSERT INTO Dish (DishName, FoodType, ImagePath, Price, IngredientStatus, DishID, StallNumber) VALUES (?, ?, ?, ?, ?, ?, ?)";
    public static final String SQL_UPD_DISH =
            "UPDATE Dish SET DishName = ?, FoodType = ?, ImagePath = ?, Price = ?, IngredientStatus = ? WHERE DishID = ? AND StallNumber = ?";
    public static final String SQL_DEL_DISH = "DELETE FROM Dish WHERE DishID = ? AND StallNumber = ?";
    public static final String SQL_INS_DISH_INGREDIENT = "INSERT INTO DishIngredient (DishID, IngredientID) VALUES (?, ?)";
    public static final String SQL_DEL_DISH_INGREDIENTS = "DELETE FROM DishIngredient WHERE DishID = ?";

    private DishControl() {}
    public static DishControl getInstance() { return instance; }

    public ArrayList<Stall> getAllStalls() {
        try (Connection conn = Database.open(); PreparedStatement stmt = conn.prepareStatement(SQL_SEL_STALLS)) {
            return readStalls(stmt);
        } catch (SQLException e) { throw readFailure(e); }
    }

    public ArrayList<Stall> getStallsForRepresentative(int employeeId) {
        String sql = "SELECT s.StallNumber, s.StallName, s.FloorNumber, s.XCoord, s.YCoord FROM Stall s " +
                "INNER JOIN StallRepresentative r ON s.StallNumber=r.StallNumber WHERE r.EmployeeID=? ORDER BY s.StallNumber";
        try (Connection conn = Database.open(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, employeeId);
            return readStalls(stmt);
        } catch (SQLException e) { throw readFailure(e); }
    }

    private ArrayList<Stall> readStalls(PreparedStatement stmt) throws SQLException {
        ArrayList<Stall> stalls = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) stalls.add(new Stall(rs.getInt("StallNumber"), rs.getString("StallName"),
                    rs.getInt("FloorNumber"), rs.getDouble("XCoord"), rs.getDouble("YCoord")));
        }
        return stalls;
    }

    public ArrayList<Dish> getDishesByStall(int stallNumber) {
        try (Connection conn = Database.open(); PreparedStatement stmt = conn.prepareStatement(SQL_SEL_DISHES_BY_STALL)) {
            stmt.setInt(1, stallNumber);
            ArrayList<Dish> dishes = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Dish dish = new Dish(rs.getInt("DishID"), rs.getInt("StallNumber"), rs.getString("DishName"),
                            rs.getString("FoodType"), rs.getString("ImagePath"), rs.getDouble("Price"));
                    dish.setIngredientStatus(rs.getString("IngredientStatus"));
                    dish.setIngredients(readIngredients(conn, dish.getDishId()));
                    dishes.add(dish);
                }
            }
            return dishes;
        } catch (SQLException e) { throw readFailure(e); }
    }

    public ArrayList<Ingredient> getIngredientsForDish(int dishId) {
        try (Connection conn = Database.open()) {
            return readIngredients(conn, dishId);
        } catch (SQLException e) { throw readFailure(e); }
    }

    private ArrayList<Ingredient> readIngredients(Connection conn, int dishId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_SEL_INGREDIENTS_BY_DISH)) {
            stmt.setInt(1, dishId);
            return readIngredients(stmt);
        }
    }

    private ArrayList<Ingredient> readIngredients(PreparedStatement stmt) throws SQLException {
        ArrayList<Ingredient> ingredients = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) ingredients.add(new Ingredient(rs.getInt("IngredientID"), rs.getString("IngredientName")));
        }
        return ingredients;
    }

    public ArrayList<Ingredient> getAllIngredients() {
        try (Connection conn = Database.open(); PreparedStatement stmt = conn.prepareStatement(SQL_SEL_ALL_INGREDIENTS)) {
            return readIngredients(stmt);
        } catch (SQLException e) { throw readFailure(e); }
    }

    public boolean addDish(Dish dish, int employeeId) {
        validateDish(dish);
        int id = Database.transaction(conn -> {
            requireRepresentative(conn, dish.getStallNumber(), employeeId);
            int nextId;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT MAX(DishID) FROM Dish"); ResultSet rs = stmt.executeQuery()) {
                rs.next(); nextId = Math.addExact(rs.getInt(1), 1);
            }
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INS_DISH)) {
                bindDish(stmt, dish, nextId);
                stmt.executeUpdate();
            }
            saveIngredients(conn, nextId, dish.getIngredients());
            return nextId;
        });
        dish.setDishId(id);
        return true;
    }

    public boolean updateDish(Dish dish, int employeeId) {
        validateDish(dish);
        Validation.positive(dish.getDishId(), "Dish ID");
        return Database.transaction(conn -> {
            requireRepresentative(conn, dish.getStallNumber(), employeeId);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPD_DISH)) {
                bindDish(stmt, dish, dish.getDishId());
                if (stmt.executeUpdate() != 1) throw new IllegalArgumentException("Dish not found in this stall. Reload the dish list.");
            }
            saveIngredients(conn, dish.getDishId(), dish.getIngredients());
            return true;
        });
    }

    private void bindDish(PreparedStatement stmt, Dish dish, int id) throws SQLException {
        stmt.setString(1, dish.getName().trim());
        stmt.setString(2, dish.getFoodType().trim());
        stmt.setString(3, dish.getImagePath().trim());
        stmt.setBigDecimal(4, java.math.BigDecimal.valueOf(dish.getPrice()));
        stmt.setString(5, dish.getIngredientStatus());
        stmt.setInt(6, id);
        stmt.setInt(7, dish.getStallNumber());
    }

    private void saveIngredients(Connection conn, int dishId, ArrayList<Ingredient> ingredients) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_DEL_DISH_INGREDIENTS)) {
            stmt.setInt(1, dishId); stmt.executeUpdate();
        }
        try (PreparedStatement stmt = conn.prepareStatement(SQL_INS_DISH_INGREDIENT)) {
            for (Ingredient ingredient : ingredients) {
                stmt.setInt(1, dishId); stmt.setInt(2, ingredient.getIngredientId()); stmt.executeUpdate();
            }
        }
    }

    public boolean deleteDish(int dishId, int stallNumber, int employeeId) {
        Validation.positive(dishId, "Dish ID");
        return Database.transaction(conn -> {
            requireRepresentative(conn, stallNumber, employeeId);
            try (PreparedStatement stmt = conn.prepareStatement("SELECT DishID FROM Dish WHERE DishID=? AND StallNumber=?")) {
                stmt.setInt(1, dishId); stmt.setInt(2, stallNumber);
                try (ResultSet rs = stmt.executeQuery()) { if (!rs.next()) return false; }
            }
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DEL_DISH_INGREDIENTS)) {
                stmt.setInt(1, dishId); stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DEL_DISH)) {
                stmt.setInt(1, dishId); stmt.setInt(2, stallNumber);
                return stmt.executeUpdate() == 1;
            }
        });
    }

    private void requireRepresentative(Connection conn, int stallNumber, int employeeId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT EmployeeID FROM StallRepresentative WHERE StallNumber=? AND EmployeeID=?")) {
            stmt.setInt(1, stallNumber); stmt.setInt(2, employeeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("This employee is not a representative of the selected stall.");
            }
        }
    }

    private void validateDish(Dish dish) {
        if (dish == null) throw new IllegalArgumentException("Select a dish.");
        Validation.positive(dish.getStallNumber(), "Stall number");
        Validation.text(dish.getName(), "Dish name", 100);
        Validation.text(dish.getFoodType(), "Food type", 45);
        Validation.text(dish.getImagePath(), "Image path", 220);
        if (!Double.isFinite(dish.getPrice()) || dish.getPrice() < 0 || dish.getPrice() > 922337203685477.5807
                || java.math.BigDecimal.valueOf(dish.getPrice()).stripTrailingZeros().scale() > 4) {
            throw new IllegalArgumentException("Price must be a non-negative Access currency value with up to four decimal places.");
        }
        if (dish.getIngredientStatus() == null || !List.of("GENERATED", "CONFIRMED", "EDITED").contains(dish.getIngredientStatus())) {
            throw new IllegalArgumentException("Select an ingredient review status.");
        }
        if (dish.getIngredients() == null) throw new IllegalArgumentException("Supply an ingredient list.");
        HashSet<Integer> ids = new HashSet<>();
        for (Ingredient ingredient : dish.getIngredients()) {
            if (ingredient == null || ingredient.getIngredientId() <= 0 || !ids.add(ingredient.getIngredientId())) {
                throw new IllegalArgumentException("Ingredients must have positive, unique IDs.");
            }
        }
    }

    public void saveIngredient(Ingredient ingredient) {
        if (ingredient == null || ingredient.getIngredientId() < 0) throw new IllegalArgumentException("Select a valid ingredient.");
        String name = Validation.text(ingredient.getIngredientName(), "Ingredient name", 80);
        int id = Database.transaction(conn -> {
            int ingredientId = ingredient.getIngredientId();
            if (ingredientId == 0) {
                try (PreparedStatement stmt = conn.prepareStatement("SELECT MAX(IngredientID) FROM Ingredient"); ResultSet rs = stmt.executeQuery()) {
                    rs.next(); ingredientId = Math.addExact(rs.getInt(1), 1);
                }
                try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO Ingredient (IngredientName, IngredientID) VALUES (?, ?)")) {
                    stmt.setString(1, name); stmt.setInt(2, ingredientId); stmt.executeUpdate();
                }
            } else {
                try (PreparedStatement stmt = conn.prepareStatement("UPDATE Ingredient SET IngredientName=? WHERE IngredientID=?")) {
                    stmt.setString(1, name); stmt.setInt(2, ingredientId);
                    if (stmt.executeUpdate() != 1) throw new IllegalArgumentException("Ingredient not found. Reload the list.");
                }
            }
            return ingredientId;
        });
        ingredient.setIngredientId(id);
        ingredient.setIngredientName(name);
    }

    public boolean deleteIngredient(int ingredientId) {
        Validation.positive(ingredientId, "Ingredient ID");
        return Database.transaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM Ingredient WHERE IngredientID=?")) {
                stmt.setInt(1, ingredientId); return stmt.executeUpdate() == 1;
            }
        });
    }

    public ArrayList<Allergen> getAllergenWarningsForDish(int dishId) {
        return getAllergenWarnings(getIngredientsForDish(dishId));
    }

    public ArrayList<Allergen> getAllergenWarnings(ArrayList<Ingredient> ingredients) {
        if (ingredients == null) throw new IllegalArgumentException("Supply an ingredient list.");
        if (ingredients.isEmpty()) return new ArrayList<>();
        String placeholders = String.join(",", java.util.Collections.nCopies(ingredients.size(), "?"));
        String sql = "SELECT a.AllergenCode, a.AllergenName, a.Description, a.CatalogID, i.IngredientID, i.IngredientName " +
                "FROM (Allergen a INNER JOIN IngredientAllergen ia ON a.AllergenCode=ia.AllergenCode) " +
                "INNER JOIN Ingredient i ON ia.IngredientID=i.IngredientID WHERE i.IngredientID IN (" + placeholders + ") ORDER BY a.AllergenName";
        try (Connection conn = Database.open(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < ingredients.size(); i++) stmt.setInt(i + 1, ingredients.get(i).getIngredientId());
            return readAllergens(stmt);
        } catch (SQLException e) { throw readFailure(e); }
    }

    static ArrayList<Allergen> readAllergens(PreparedStatement stmt) throws SQLException {
        Map<String, Allergen> allergens = new LinkedHashMap<>();
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String code = rs.getString("AllergenCode");
                Allergen allergen = allergens.get(code);
                if (allergen == null) {
                    allergen = new Allergen(code, rs.getString("AllergenName"), rs.getString("Description"), rs.getInt("CatalogID"));
                    allergens.put(code, allergen);
                }
                int ingredientId = rs.getInt("IngredientID");
                if (!rs.wasNull()) allergen.getTriggeringIngredients().add(new Ingredient(ingredientId, rs.getString("IngredientName")));
            }
        }
        return new ArrayList<>(allergens.values());
    }

    private IllegalStateException readFailure(SQLException e) {
        return new IllegalStateException("Cannot load database information. " + e.getMessage(), e);
    }
}
