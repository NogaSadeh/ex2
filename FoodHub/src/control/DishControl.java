package control;

import Objects.Allergen;
import Objects.Dish;
import Objects.Ingredient;
import Objects.Stall;

import java.sql.*;
import java.util.ArrayList;

public class DishControl {
    private static DishControl instance;

    public static final String SQL_SEL_STALLS =
            "SELECT StallNumber, StallName, Floor, X, Y FROM Stall";
    public static final String SQL_SEL_DISHES_BY_STALL =
            "SELECT DishID, StallNumber, DishName, FoodType, ImagePath, Price FROM Dish WHERE StallNumber = ?";
    public static final String SQL_SEL_INGREDIENTS_BY_DISH =
            "SELECT i.IngredientID, i.IngredientName FROM Ingredient i " +
            "INNER JOIN DishIngredient di ON i.IngredientID = di.IngredientID WHERE di.DishID = ?";
    public static final String SQL_SEL_ALL_INGREDIENTS =
            "SELECT IngredientID, IngredientName FROM Ingredient ORDER BY IngredientName";
    public static final String SQL_INS_DISH =
            "INSERT INTO Dish (StallNumber, DishName, FoodType, ImagePath, Price) VALUES (?, ?, ?, ?, ?)";
    public static final String SQL_UPD_DISH =
            "UPDATE Dish SET DishName = ?, FoodType = ?, ImagePath = ?, Price = ? WHERE DishID = ?";
    public static final String SQL_DEL_DISH =
            "DELETE FROM Dish WHERE DishID = ?";
    public static final String SQL_INS_DISH_INGREDIENT =
            "INSERT INTO DishIngredient (DishID, IngredientID) VALUES (?, ?)";
    public static final String SQL_DEL_DISH_INGREDIENTS =
            "DELETE FROM DishIngredient WHERE DishID = ?";
    public static final String SQL_SEL_DISH_ALLERGENS =
            "SELECT DISTINCT a.AllergenCode, a.AllergenName, a.Description, a.CatalogID " +
            "FROM (Allergen a INNER JOIN IngredientAllergen ia ON a.AllergenCode = ia.AllergenCode) " +
            "INNER JOIN DishIngredient di ON ia.IngredientID = di.IngredientID WHERE di.DishID = ?";

    private DishControl() {}

    public static DishControl getInstance() {
        if (instance == null) {
            instance = new DishControl();
        }
        return instance;
    }

    public ArrayList<Stall> getAllStalls() {
        ArrayList<Stall> stalls = new ArrayList<>();
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
            try (Connection conn = DriverManager.getConnection(Consts.CONN_STR);
                 PreparedStatement stmt = conn.prepareStatement(SQL_SEL_STALLS);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    stalls.add(new Stall(
                            rs.getInt("StallNumber"),
                            rs.getString("StallName"),
                            rs.getInt("Floor"),
                            rs.getInt("X"),
                            rs.getInt("Y")
                    ));
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return stalls;
    }

    public ArrayList<Dish> getDishesByStall(int stallNumber) {
        ArrayList<Dish> dishes = new ArrayList<>();
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
            try (Connection conn = DriverManager.getConnection(Consts.CONN_STR);
                 PreparedStatement stmt = conn.prepareStatement(SQL_SEL_DISHES_BY_STALL)) {

                stmt.setInt(1, stallNumber);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Dish dish = new Dish(
                                rs.getInt("DishID"),
                                rs.getInt("StallNumber"),
                                rs.getString("DishName"),
                                rs.getString("FoodType"),
                                rs.getString("ImagePath"),
                                rs.getDouble("Price")
                        );
                        dish.setIngredients(getIngredientsForDish(dish.getDishId()));
                        dishes.add(dish);
                    }
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return dishes;
    }

    public ArrayList<Ingredient> getIngredientsForDish(int dishId) {
        ArrayList<Ingredient> list = new ArrayList<>();
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
            try (Connection conn = DriverManager.getConnection(Consts.CONN_STR);
                 PreparedStatement stmt = conn.prepareStatement(SQL_SEL_INGREDIENTS_BY_DISH)) {

                stmt.setInt(1, dishId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(new Ingredient(
                                rs.getInt("IngredientID"),
                                rs.getString("IngredientName")
                        ));
                    }
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return list;
    }

    public ArrayList<Ingredient> getAllIngredients() {
        ArrayList<Ingredient> list = new ArrayList<>();
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
            try (Connection conn = DriverManager.getConnection(Consts.CONN_STR);
                 PreparedStatement stmt = conn.prepareStatement(SQL_SEL_ALL_INGREDIENTS);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    list.add(new Ingredient(
                            rs.getInt("IngredientID"),
                            rs.getString("IngredientName")
                    ));
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean addDish(Dish dish) {
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
            try (Connection conn = DriverManager.getConnection(Consts.CONN_STR);
                 PreparedStatement stmt = conn.prepareStatement(SQL_INS_DISH, Statement.RETURN_GENERATED_KEYS)) {

                int i = 1;
                stmt.setInt(i++, dish.getStallNumber());
                stmt.setString(i++, dish.getName());
                stmt.setString(i++, dish.getFoodType());
                if (dish.getImagePath() != null && !dish.getImagePath().trim().isEmpty()) {
                    stmt.setString(i++, dish.getImagePath().trim());
                } else {
                    stmt.setNull(i++, java.sql.Types.VARCHAR);
                }
                stmt.setDouble(i++, dish.getPrice());

                if (stmt.executeUpdate() == 0) return false;

                int newDishId = -1;
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        newDishId = keys.getInt(1);
                    }
                }

                if (newDishId != -1 && dish.getIngredients() != null) {
                    try (PreparedStatement ingStmt = conn.prepareStatement(SQL_INS_DISH_INGREDIENT)) {
                        for (Ingredient ing : dish.getIngredients()) {
                            ingStmt.setInt(1, newDishId);
                            ingStmt.setInt(2, ing.getIngredientId());
                            ingStmt.executeUpdate();
                        }
                    }
                }
                return true;
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateDish(Dish dish) {
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
            try (Connection conn = DriverManager.getConnection(Consts.CONN_STR)) {
                try (PreparedStatement stmt = conn.prepareStatement(SQL_UPD_DISH)) {
                    int i = 1;
                    stmt.setString(i++, dish.getName());
                    stmt.setString(i++, dish.getFoodType());
                    if (dish.getImagePath() != null && !dish.getImagePath().trim().isEmpty()) {
                        stmt.setString(i++, dish.getImagePath().trim());
                    } else {
                        stmt.setNull(i++, java.sql.Types.VARCHAR);
                    }
                    stmt.setDouble(i++, dish.getPrice());
                    stmt.setInt(i++, dish.getDishId());
                    stmt.executeUpdate();
                }

                try (PreparedStatement delStmt = conn.prepareStatement(SQL_DEL_DISH_INGREDIENTS)) {
                    delStmt.setInt(1, dish.getDishId());
                    delStmt.executeUpdate();
                }

                if (dish.getIngredients() != null) {
                    try (PreparedStatement insStmt = conn.prepareStatement(SQL_INS_DISH_INGREDIENT)) {
                        for (Ingredient ing : dish.getIngredients()) {
                            insStmt.setInt(1, dish.getDishId());
                            insStmt.setInt(2, ing.getIngredientId());
                            insStmt.executeUpdate();
                        }
                    }
                }
                return true;
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteDish(int dishId) {
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
            try (Connection conn = DriverManager.getConnection(Consts.CONN_STR)) {
                try (PreparedStatement s1 = conn.prepareStatement(SQL_DEL_DISH_INGREDIENTS)) {
                    s1.setInt(1, dishId);
                    s1.executeUpdate();
                }
                try (PreparedStatement s2 = conn.prepareStatement(SQL_DEL_DISH)) {
                    s2.setInt(1, dishId);
                    return s2.executeUpdate() > 0;
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    public ArrayList<Allergen> getAllergenWarningsForDish(int dishId) {
        ArrayList<Allergen> warnings = new ArrayList<>();
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
            try (Connection conn = DriverManager.getConnection(Consts.CONN_STR);
                 PreparedStatement stmt = conn.prepareStatement(SQL_SEL_DISH_ALLERGENS)) {

                stmt.setInt(1, dishId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        warnings.add(new Allergen(
                                rs.getString("AllergenCode"),
                                rs.getString("AllergenName"),
                                rs.getString("Description"),
                                rs.getInt("CatalogID")
                        ));
                    }
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return warnings;
    }
}