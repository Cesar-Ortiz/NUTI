package model;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Representa un vehículo (agente) en la simulación.
 * Thread-safe: los campos mutables usan volatile y AtomicInteger
 * para permitir acceso concurrente seguro desde múltiples hilos.
 */
public class Vehicle {
    private static final AtomicInteger nextId = new AtomicInteger(1);

    private final int id;
    private volatile Position position;
    private volatile Direction direction;
    private volatile boolean stopped;
    private final AtomicInteger totalMoves = new AtomicInteger(0);

    public Vehicle(Position position, Direction direction) {
        this.id = nextId.getAndIncrement();
        this.position = position;
        this.direction = direction;
        this.stopped = false;
    }

    public void move(Position newPosition) {
        this.position = newPosition;
        this.stopped = false;
        this.totalMoves.incrementAndGet();
    }

    public void stop() {
        this.stopped = true;
    }

    public void setDirection(Direction newDirection) {
        this.direction = newDirection;
    }

    public Position getNextPosition() {
        return position.move(direction);
    }

    public int getId()            { return id; }
    public Position getPosition() { return position; }
    public Direction getDirection(){ return direction; }
    public boolean isStopped()    { return stopped; }
    public int getTotalMoves()    { return totalMoves.get(); }

    @Override
    public String toString() {
        return "Vehicle{id=" + id + ", pos=" + position +
               ", dir=" + direction + ", stopped=" + stopped + '}';
    }
}
