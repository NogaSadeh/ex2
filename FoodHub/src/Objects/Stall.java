package Objects;

public class Stall {
    private int stallNumber;
    private String name;
    private int floor;
    private int x;
    private int y;

    public Stall(int stallNumber, String name, int floor, int x, int y) {
        this.stallNumber = stallNumber;
        this.name = name;
        this.floor = floor;
        this.x = x;
        this.y = y;
    }

    public int getStallNumber() { return stallNumber; }
    public String getName() { return name; }
    public int getFloor() { return floor; }
    public int getX() { return x; }
    public int getY() { return y; }

    @Override
    public String toString() {
        return stallNumber + " - " + name + " (Floor " + floor + ")";
    }
}