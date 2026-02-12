package model;

/**
 * Representa un semáforo en una intersección
 */
public class TrafficLight {
    private final Position position;
    private Direction allowedDirection;
    private int cycleCounter;
    private final int cycleDuration;
    
    /**
     * @param position Posición de la intersección
     * @param cycleDuration Duración de cada fase del semáforo (en pasos de tiempo)
     */
    public TrafficLight(Position position, int cycleDuration) {
        this.position = position;
        this.cycleDuration = cycleDuration;
        this.cycleCounter = 0;
        // Inicialmente permite tráfico en dirección Norte-Sur
        this.allowedDirection = Direction.NORTH;
    }
    
    /**
     * Actualiza el estado del semáforo
     */
    public void update() {
        cycleCounter++;
        if (cycleCounter >= cycleDuration) {
            cycleCounter = 0;
            toggleDirection();
        }
    }
    
    /**
     * Alterna la dirección permitida entre Norte-Sur y Este-Oeste
     */
    private void toggleDirection() {
        if (allowedDirection == Direction.NORTH || allowedDirection == Direction.SOUTH) {
            allowedDirection = Direction.EAST;
        } else {
            allowedDirection = Direction.WEST;
        }
    }
    
    /**
     * Verifica si un vehículo puede pasar según su dirección
     */
    public boolean canPass(Direction vehicleDirection) {
        // Permite pasar si la dirección del vehículo coincide con la permitida
        // o si es la opuesta (mismo eje)
        return vehicleDirection == allowedDirection || 
               vehicleDirection == allowedDirection.opposite();
    }
    
    public Position getPosition() {
        return position;
    }
    
    public Direction getAllowedDirection() {
        return allowedDirection;
    }
}
