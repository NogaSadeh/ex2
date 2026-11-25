package Entity;

public class Stall {
    private int stallNumber;
    private String name;
    private int floor;
    private double x;
    private double y;

    public Stall(int stallNumber, String name, int floor, double x, double y) {
        this.stallNumber = stallNumber;
        this.name = name;
        this.floor = floor;
        this.x = x;
        this.y = y;
    }

    public int getStallNumber() { return stallNumber; }
    public String getName() { return name; }
    public int getFloor() { return floor; }
    public double getX() { return x; }
    public double getY() { return y; }
    public void setStallNumber(int stallNumber) { this.stallNumber = stallNumber; }
    public void setName(String name) { this.name = name; }
    public void setFloor(int floor) { this.floor = floor; }
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }

    @Override
    public String toString() {
        return stallNumber + " - " + name + " (Floor " + floor + ")";
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof Stall && stallNumber == ((Stall) obj).stallNumber;
    }

    @Override
    public int hashCode() { return Integer.hashCode(stallNumber); }
}
