package model;

/**
 * Representa las direcciones de movimiento de los vehículos
 */
public enum Direction {
    NORTH(0, -1),
    SOUTH(0, 1),
    EAST(1, 0),
    WEST(-1, 0);
    
    private final int dx;
    private final int dy;
    
    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }
    
    public int getDx() {
        return dx;
    }
    
    public int getDy() {
        return dy;
    }
    
    /**
     * Retorna la dirección opuesta
     */
    public Direction opposite() {
        switch (this) {
            case NORTH: return SOUTH;
            case SOUTH: return NORTH;
            case EAST: return WEST;
            case WEST: return EAST;
            default: return this;
        }
    }
    
    /**
     * Retorna una dirección perpendicular aleatoria
     */
    public Direction[] perpendiculars() {
        switch (this) {
            case NORTH:
            case SOUTH:
                return new Direction[]{EAST, WEST};
            case EAST:
            case WEST:
                return new Direction[]{NORTH, SOUTH};
            default:
                return new Direction[]{};
        }
    }
}
