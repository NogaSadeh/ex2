package control;

import Entity.Allergen;
import Entity.AllergenCatalog;
import Entity.Ingredient;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class AllergenControl {
    private static final AllergenControl instance = new AllergenControl();
    private AllergenControl() {}
    public static AllergenControl getInstance() { return instance; }

    public ArrayList<Allergen> getAllergens() {
        String sql = "SELECT a.AllergenCode, a.AllergenName, a.Description, a.CatalogID, i.IngredientID, i.IngredientName " +
                "FROM (Allergen a LEFT JOIN IngredientAllergen ia ON a.AllergenCode=ia.AllergenCode) " +
                "LEFT JOIN Ingredient i ON ia.IngredientID=i.IngredientID ORDER BY a.AllergenName, i.IngredientName";
        try (Connection conn = Database.open(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            return DishControl.readAllergens(stmt);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot load the allergen catalog. " + e.getMessage(), e);
        }
    }

    public AllergenCatalog parseCatalog(Path path) {
        try {
            int maxBytes = Integer.getInteger("foodhub.catalog.maxBytes", 5 * 1024 * 1024);
            if (maxBytes <= 0 || maxBytes == Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid catalog size limit.");
            byte[] bytes;
            try (InputStream input = Files.newInputStream(path)) { bytes = input.readNBytes(maxBytes + 1); }
            if (bytes.length > maxBytes) throw new IllegalArgumentException("The XML catalog exceeds " + maxBytes + " bytes.");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void error(SAXParseException e) throws SAXException { throw e; }
                @Override
                public void fatalError(SAXParseException e) throws SAXException { throw e; }
            });
            Element root = builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
            if (!root.getTagName().equals("allergenCatalog")) throw new IllegalArgumentException("Expected an allergenCatalog root element.");
            int catalogId = positiveAttribute(root, "id");
            LocalDate updated = LocalDate.parse(root.getAttribute("lastUpdated"));
            ArrayList<Allergen> allergens = new ArrayList<>();
            HashSet<String> codes = new HashSet<>();
            HashMap<Integer, String> ingredientNames = new HashMap<>();
            for (Element element : children(root, "allergen")) {
                String code = Validation.text(element.getAttribute("code"), "Allergen code", 20).toUpperCase(Locale.ROOT);
                if (!codes.add(code)) throw new IllegalArgumentException("Duplicate allergen code: " + code);
                String name = Validation.text(element.getAttribute("name"), "Allergen name", 75);
                String description = Validation.text(element.getAttribute("description"), "Allergen description", 250);
                Allergen allergen = new Allergen(code, name, description, catalogId);
                HashSet<Integer> ids = new HashSet<>();
                for (Element ingredientElement : children(element, "ingredient")) {
                    int id = positiveAttribute(ingredientElement, "id");
                    String ingredientName = Validation.text(ingredientElement.getAttribute("name"), "Ingredient name", 80);
                    if (!ids.add(id)) throw new IllegalArgumentException("Duplicate ingredient ID in " + code + ": " + id);
                    String previous = ingredientNames.putIfAbsent(id, ingredientName);
                    if (previous != null && !previous.equals(ingredientName)) throw new IllegalArgumentException("Conflicting names for ingredient " + id);
                    if (!children(ingredientElement, "").isEmpty()) throw new IllegalArgumentException("Ingredient elements must be empty.");
                    allergen.getTriggeringIngredients().add(new Ingredient(id, ingredientName));
                }
                allergens.add(allergen);
            }
            if (allergens.isEmpty()) throw new IllegalArgumentException("The catalog must contain at least one allergen.");
            return new AllergenCatalog(catalogId, updated, allergens);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot read the XML catalog: " + e.getMessage(), e);
        }
    }

    private int positiveAttribute(Element element, String name) {
        return Validation.positive(Integer.parseInt(element.getAttribute(name)), name);
    }

    private ArrayList<Element> children(Element parent, String expectedTag) {
        ArrayList<Element> elements = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element) {
                Element child = (Element) node;
                if (!child.getTagName().equals(expectedTag)) throw new IllegalArgumentException("Unexpected XML element: " + child.getTagName());
                elements.add(child);
            } else if (node.getNodeType() == Node.TEXT_NODE && !node.getTextContent().isBlank()) {
                throw new IllegalArgumentException("Unexpected text in " + parent.getTagName());
            }
        }
        return elements;
    }

    public int importCatalog(Path path) {
        AllergenCatalog catalog = parseCatalog(path);
        return Database.transaction(conn -> {
            Timestamp updated = Timestamp.valueOf(catalog.getLastUpdated().atStartOfDay());
            try (PreparedStatement stmt = conn.prepareStatement("SELECT LastUpdated FROM AllergenCatalog WHERE CatalogID=?")) {
                stmt.setInt(1, catalog.getCatalogId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getTimestamp(1).after(updated)) throw new IllegalArgumentException("A newer version of this catalog is already installed.");
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE AllergenCatalog SET LastUpdated=? WHERE CatalogID=?")) {
                stmt.setTimestamp(1, updated); stmt.setInt(2, catalog.getCatalogId());
                if (stmt.executeUpdate() == 0) {
                    try (PreparedStatement insert = conn.prepareStatement("INSERT INTO AllergenCatalog (LastUpdated, CatalogID) VALUES (?, ?)")) {
                        insert.setTimestamp(1, updated); insert.setInt(2, catalog.getCatalogId()); insert.executeUpdate();
                    }
                }
            }
            HashSet<String> incoming = new HashSet<>();
            for (Allergen allergen : catalog.getAllergens()) {
                incoming.add(allergen.getAllergenCode());
                saveAllergen(conn, allergen, updated);
            }
            ArrayList<String> removed = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement("SELECT AllergenCode FROM Allergen WHERE CatalogID=?")) {
                stmt.setInt(1, catalog.getCatalogId());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) if (!incoming.contains(rs.getString(1).toUpperCase(Locale.ROOT))) removed.add(rs.getString(1));
                }
            }
            for (String code : removed) {
                deleteLinks(conn, code);
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM Allergen WHERE AllergenCode=?")) {
                    stmt.setString(1, code); stmt.executeUpdate();
                }
            }
            return incoming.size();
        });
    }

    private void saveAllergen(Connection conn, Allergen allergen, Timestamp updated) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT c.LastUpdated FROM Allergen a INNER JOIN AllergenCatalog c ON a.CatalogID=c.CatalogID WHERE a.AllergenCode=?")) {
            stmt.setString(1, allergen.getAllergenCode());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getTimestamp(1).after(updated)) throw new IllegalArgumentException("A newer catalog contains allergen " + allergen.getAllergenCode());
            }
        }
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE Allergen SET AllergenName=?, Description=?, CatalogID=? WHERE AllergenCode=?")) {
            bindAllergen(stmt, allergen);
            if (stmt.executeUpdate() == 0) {
                try (PreparedStatement insert = conn.prepareStatement("INSERT INTO Allergen (AllergenName, Description, CatalogID, AllergenCode) VALUES (?, ?, ?, ?)")) {
                    bindAllergen(insert, allergen); insert.executeUpdate();
                }
            }
        }
        deleteLinks(conn, allergen.getAllergenCode());
        for (Ingredient ingredient : allergen.getTriggeringIngredients()) {
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE Ingredient SET IngredientName=? WHERE IngredientID=?")) {
                stmt.setString(1, ingredient.getIngredientName()); stmt.setInt(2, ingredient.getIngredientId());
                if (stmt.executeUpdate() == 0) {
                    try (PreparedStatement insert = conn.prepareStatement("INSERT INTO Ingredient (IngredientName, IngredientID) VALUES (?, ?)")) {
                        insert.setString(1, ingredient.getIngredientName()); insert.setInt(2, ingredient.getIngredientId()); insert.executeUpdate();
                    }
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO IngredientAllergen (IngredientID, AllergenCode) VALUES (?, ?)")) {
                stmt.setInt(1, ingredient.getIngredientId()); stmt.setString(2, allergen.getAllergenCode()); stmt.executeUpdate();
            }
        }
    }

    private void bindAllergen(PreparedStatement stmt, Allergen allergen) throws SQLException {
        stmt.setString(1, allergen.getName()); stmt.setString(2, allergen.getDescription());
        stmt.setInt(3, allergen.getCatalogId()); stmt.setString(4, allergen.getAllergenCode());
    }

    private void deleteLinks(Connection conn, String code) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM IngredientAllergen WHERE AllergenCode=?")) {
            stmt.setString(1, code); stmt.executeUpdate();
        }
    }
}
