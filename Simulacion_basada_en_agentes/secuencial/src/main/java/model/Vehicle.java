package model;

import java.util.Random;

/**
 * Representa un vehículo (agente) en la simulación
 */
public class Vehicle {
    private static int nextId = 1;
    private static final Random random = new Random();
    
    private final int id;
    private Position position;
    private Direction direction;
    private boolean stopped;
    private int totalMoves;
    
    public Vehicle(Position position, Direction direction) {
        this.id = nextId++;
        this.position = position;
        this.direction = direction;
        this.stopped = false;
        this.totalMoves = 0;
    }
    
    /**
     * Intenta mover el vehículo a la siguiente celda
     */
    public void move(Position newPosition) {
        this.position = newPosition;
        this.stopped = false;
        this.totalMoves++;
    }
    
    /**
     * Detiene el vehículo
     */
    public void stop() {
        this.stopped = true;
    }
    
    /**
     * Cambia la dirección del vehículo con cierta probabilidad
     * (usado en intersecciones)
     */
    public void maybeChangeDirection(double probability) {
        if (random.nextDouble() < probability) {
            Direction[] perpendiculars = direction.perpendiculars();
            if (perpendiculars.length > 0) {
                direction = perpendiculars[random.nextInt(perpendiculars.length)];
            }
        }
    }
    
    /**
     * Establece la dirección del vehículo (para giros forzados)
     */
    public void setDirection(Direction newDirection) {
        this.direction = newDirection;
    }
    
    /**
     * Calcula la siguiente posición basándose en la dirección actual
     */
    public Position getNextPosition() {
        return position.move(direction);
    }
    
    public int getId() {
        return id;
    }
    
    public Position getPosition() {
        return position;
    }
    
    public Direction getDirection() {
        return direction;
    }
    
    public boolean isStopped() {
        return stopped;
    }
    
    public int getTotalMoves() {
        return totalMoves;
    }
    
    @Override
    public String toString() {
        return "Vehicle{" +
                "id=" + id +
                ", pos=" + position +
                ", dir=" + direction +
                ", stopped=" + stopped +
                '}';
    }
}
