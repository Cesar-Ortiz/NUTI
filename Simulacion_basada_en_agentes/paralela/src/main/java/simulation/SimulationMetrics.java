package simulation;

/**
 * Almacena métricas del sistema de tráfico
 */
public class SimulationMetrics {
    private int totalVehicles;
    private int stoppedVehicles;
    private int movingVehicles;
    private int totalMoves;
    private long executionTimeMs;
    
    public SimulationMetrics() {
        reset();
    }
    
    public void reset() {
        this.totalVehicles = 0;
        this.stoppedVehicles = 0;
        this.movingVehicles = 0;
        this.totalMoves = 0;
        this.executionTimeMs = 0;
    }
    
    public void update(int totalVehicles, int stoppedVehicles, int totalMoves) {
        this.totalVehicles = totalVehicles;
        this.stoppedVehicles = stoppedVehicles;
        this.movingVehicles = totalVehicles - stoppedVehicles;
        this.totalMoves = totalMoves;
    }
    
    public void setExecutionTime(long timeMs) {
        this.executionTimeMs = timeMs;
    }
    
    public int getTotalVehicles() {
        return totalVehicles;
    }
    
    public int getStoppedVehicles() {
        return stoppedVehicles;
    }
    
    public int getMovingVehicles() {
        return movingVehicles;
    }
    
    public int getTotalMoves() {
        return totalMoves;
    }
    
    public double getAverageFlow() {
        if (totalVehicles == 0) return 0.0;
        return (double) movingVehicles / totalVehicles;
    }
    
    public double getStopPercentage() {
        if (totalVehicles == 0) return 0.0;
        return (double) stoppedVehicles / totalVehicles * 100.0;
    }
    
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }
    
    @Override
    public String toString() {
        return String.format(
            " Vehículos totales: %d\n" +
            " Vehículos detenidos: %d (%.2f%%)\n" +
            " Vehículos en movimiento: %d\n" +
            " Flujo promedio: %.2f\n" +
            " Movimientos totales: %d\n" +
            " Tiempo de ejecución: %d ms",
            totalVehicles, stoppedVehicles, getStopPercentage(),
            movingVehicles, getAverageFlow(), totalMoves, executionTimeMs
        );
    }
}
