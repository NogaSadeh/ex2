package Entity;

import java.time.LocalDate;
import java.util.ArrayList;

public class AllergenCatalog {
    private final int catalogId;
    private final LocalDate lastUpdated;
    private final ArrayList<Allergen> allergens;

    public AllergenCatalog(int catalogId, LocalDate lastUpdated, ArrayList<Allergen> allergens) {
        this.catalogId = catalogId;
        this.lastUpdated = lastUpdated;
        this.allergens = new ArrayList<>(allergens);
    }

    public int getCatalogId() { return catalogId; }
    public LocalDate getLastUpdated() { return lastUpdated; }
    public ArrayList<Allergen> getAllergens() { return new ArrayList<>(allergens); }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof AllergenCatalog && catalogId == ((AllergenCatalog) obj).catalogId;
    }

    @Override
    public int hashCode() { return Integer.hashCode(catalogId); }

    @Override
    public String toString() { return catalogId + " - " + lastUpdated; }
}
