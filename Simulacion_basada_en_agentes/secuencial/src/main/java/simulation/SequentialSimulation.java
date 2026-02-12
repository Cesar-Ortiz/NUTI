package simulation;

import model.*;

import java.io.IOException;
import java.util.*;

/**
 * Simulación secuencial de tráfico urbano basada en agentes
 */
public class SequentialSimulation {
    private final Grid grid;
    private final List<Vehicle> vehicles;
    private final Map<Position, TrafficLight> trafficLights;
    private final Map<Position, List<Vehicle>> occupancy;
    private final SimulationMetrics metrics;
    private final Random random;
    
    // Parámetros de simulación
    private final int trafficLightCycleDuration;
    private final double directionChangeProb;
    private final int maxVehiclesPerCell = 2; // Bidireccional
    
    /**
     * Constructor de la simulación
     * 
     * @param gridFilePath Ruta al archivo de configuración de la rejilla
     * @param numVehicles Número de vehículos a simular
     * @param trafficLightCycleDuration Duración del ciclo de semáforos (pasos de tiempo)
     * @param directionChangeProb Probabilidad de cambio de dirección en intersecciones
     */
    public SequentialSimulation(String gridFilePath, int numVehicles, 
                                 int trafficLightCycleDuration, double directionChangeProb) 
            throws IOException {
        this.grid = new Grid(gridFilePath);
        this.vehicles = new ArrayList<>();
        this.trafficLights = new HashMap<>();
        this.occupancy = new HashMap<>();
        this.metrics = new SimulationMetrics();
        this.random = new Random();
        this.trafficLightCycleDuration = trafficLightCycleDuration;
        this.directionChangeProb = directionChangeProb;
        
        initializeTrafficLights();
        initializeVehicles(numVehicles);
    }
    
    /**
     * Inicializa los semáforos en todas las intersecciones
     */
    private void initializeTrafficLights() {
        for (Position intersection : grid.getIntersections()) {
            trafficLights.put(intersection, new TrafficLight(intersection, trafficLightCycleDuration));
        }
    }
    
    /**
     * Inicializa los vehículos en posiciones aleatorias
     */
    private void initializeVehicles(int numVehicles) {
        List<Position> traversablePositions = grid.getTraversablePositions();
        
        if (traversablePositions.isEmpty()) {
            throw new IllegalStateException("No hay posiciones transitables en la rejilla");
        }
        
        Direction[] directions = Direction.values();
        
        for (int i = 0; i < numVehicles; i++) {
            Position pos = traversablePositions.get(random.nextInt(traversablePositions.size()));
            Direction dir = directions[random.nextInt(directions.length)];
            Vehicle vehicle = new Vehicle(pos, dir);
            vehicles.add(vehicle);
            
            // Registrar ocupación
            occupancy.computeIfAbsent(pos, k -> new ArrayList<>()).add(vehicle);
        }
    }
    
    /**
     * Ejecuta un paso de tiempo de la simulación
     */
    public void step() {
        // 1. Actualizar semáforos
        updateTrafficLights();
        
        // 2. Actualizar vehículos
        updateVehicles();
        
        // 3. Actualizar métricas
        updateMetrics();
    }
    
    /**
     * Actualiza el estado de todos los semáforos
     */
    private void updateTrafficLights() {
        for (TrafficLight light : trafficLights.values()) {
            light.update();
        }
    }
    
    /**
     * Actualiza el estado de todos los vehículos
     */
    private void updateVehicles() {
        // Limpiar ocupación anterior
        occupancy.clear();
        
        // Crear mapa de movimientos planeados
        Map<Vehicle, Position> plannedMoves = new HashMap<>();
        Map<Vehicle, Direction> plannedDirections = new HashMap<>();
        
        for (Vehicle vehicle : vehicles) {
            Position currentPos = vehicle.getPosition();
            Direction currentDir = vehicle.getDirection();
            Position nextPos = vehicle.getNextPosition();
            boolean foundValidMove = false;
            Direction chosenDir = currentDir;
            
            // Verificar si puede avanzar en su dirección actual
            if (grid.isTraversable(nextPos)) {
                // Si está en intersección, verificar semáforo
                if (grid.isIntersection(currentPos)) {
                    TrafficLight light = trafficLights.get(currentPos);
                    if (light != null && !light.canPass(currentDir)) {
                        // Semáforo en rojo, intentar girar
                        Direction[] perp = currentDir.perpendiculars();
                        for (Direction testDir : perp) {
                            if (light.canPass(testDir)) {
                                Position testPos = currentPos.move(testDir);
                                if (grid.isTraversable(testPos)) {
                                    nextPos = testPos;
                                    chosenDir = testDir;
                                    foundValidMove = true;
                                    break;
                                }
                            }
                        }
                    } else {
                        // Semáforo en verde o no hay semáforo
                        foundValidMove = true;
                        // Posibilidad de cambiar dirección voluntariamente
                        if (random.nextDouble() < directionChangeProb) {
                            Direction[] perp = currentDir.perpendiculars();
                            if (perp.length > 0) {
                                Direction newDir = perp[random.nextInt(perp.length)];
                                Position testPos = currentPos.move(newDir);
                                if (grid.isTraversable(testPos)) {
                                    nextPos = testPos;
                                    chosenDir = newDir;
                                }
                            }
                        }
                    }
                } else {
                    // No está en intersección, seguir adelante
                    foundValidMove = true;
                }
            }
            
            // Si no puede moverse, intentar alternativas
            if (!foundValidMove) {
                // Si está en intersección, probar todas las direcciones disponibles
                if (grid.isIntersection(currentPos)) {
                    TrafficLight light = trafficLights.get(currentPos);
                    Direction[] allDirs = Direction.values();
                    
                    // Mezclar direcciones para variedad
                    List<Direction> dirList = new ArrayList<>(Arrays.asList(allDirs));
                    Collections.shuffle(dirList, random);
                    
                    for (Direction testDir : dirList) {
                        // Evitar retroceder si es posible
                        if (testDir == currentDir.opposite()) continue;
                        
                        if (light == null || light.canPass(testDir)) {
                            Position testPos = currentPos.move(testDir);
                            if (grid.isTraversable(testPos)) {
                                nextPos = testPos;
                                chosenDir = testDir;
                                foundValidMove = true;
                                break;
                            }
                        }
                    }
                    
                    // Si aún no encuentra movimiento, permitir retroceder
                    if (!foundValidMove) {
                        Direction reverseDir = currentDir.opposite();
                        if (light == null || light.canPass(reverseDir)) {
                            Position reversePos = currentPos.move(reverseDir);
                            if (grid.isTraversable(reversePos)) {
                                nextPos = reversePos;
                                chosenDir = reverseDir;
                                foundValidMove = true;
                            }
                        }
                    }
                } else {
                    // No está en intersección y está bloqueado - no puede hacer nada
                    foundValidMove = false;
                }
            }
            
            if (foundValidMove) {
                plannedMoves.put(vehicle, nextPos);
                plannedDirections.put(vehicle, chosenDir);
            } else {
                vehicle.stop();
                occupancy.computeIfAbsent(currentPos, k -> new ArrayList<>()).add(vehicle);
            }
        }
        
        // Contar cuántos vehículos van a cada posición en cada dirección
        Map<Position, Map<Direction, List<Vehicle>>> vehiclesByTargetAndDir = new HashMap<>();
        
        for (Map.Entry<Vehicle, Position> entry : plannedMoves.entrySet()) {
            Vehicle vehicle = entry.getKey();
            Position target = entry.getValue();
            Direction dir = plannedDirections.get(vehicle);
            
            vehiclesByTargetAndDir.computeIfAbsent(target, k -> new HashMap<>());
            vehiclesByTargetAndDir.get(target).computeIfAbsent(dir, k -> new ArrayList<>()).add(vehicle);
        }
        
        // Ejecutar movimientos respetando bidireccionalidad
        for (Map.Entry<Position, Map<Direction, List<Vehicle>>> posEntry : vehiclesByTargetAndDir.entrySet()) {
            Position targetPos = posEntry.getKey();
            Map<Direction, List<Vehicle>> dirMap = posEntry.getValue();
            
            // Para cada dirección, permitir hasta maxVehiclesPerCell/2 vehículos
            // (1 por sentido en cada eje: N-S y E-O)
            for (Map.Entry<Direction, List<Vehicle>> dirEntry : dirMap.entrySet()) {
                Direction dir = dirEntry.getKey();
                List<Vehicle> vehiclesInDir = dirEntry.getValue();
                
                // Permitir 1 vehículo por dirección (bidireccional verdadero)
                int allowedInDir = 1;
                
                // Verificar cuántos del sentido opuesto ya están yendo ahí
                Direction opposite = dir.opposite();
                int oppositeCount = dirMap.containsKey(opposite) ? dirMap.get(opposite).size() : 0;
                
                // Si hay espacio, permitir el movimiento
                for (int i = 0; i < Math.min(vehiclesInDir.size(), allowedInDir); i++) {
                    Vehicle vehicle = vehiclesInDir.get(i);
                    Direction newDir = plannedDirections.get(vehicle);
                    
                    // Cambiar dirección si es necesario
                    if (newDir != vehicle.getDirection()) {
                        vehicle.setDirection(newDir);
                    }
                    
                    vehicle.move(targetPos);
                    occupancy.computeIfAbsent(targetPos, k -> new ArrayList<>()).add(vehicle);
                }
                
                // Los demás se quedan detenidos
                for (int i = allowedInDir; i < vehiclesInDir.size(); i++) {
                    Vehicle vehicle = vehiclesInDir.get(i);
                    vehicle.stop();
                    Position currentPos = vehicle.getPosition();
                    occupancy.computeIfAbsent(currentPos, k -> new ArrayList<>()).add(vehicle);
                }
            }
        }
    }
    
    /**
     * Actualiza las métricas de la simulación
     */
    private void updateMetrics() {
        int stopped = 0;
        int totalMoves = 0;
        
        for (Vehicle vehicle : vehicles) {
            if (vehicle.isStopped()) {
                stopped++;
            }
            totalMoves += vehicle.getTotalMoves();
        }
        
        metrics.update(vehicles.size(), stopped, totalMoves);
    }
    
    /**
     * Ejecuta la simulación por un número de pasos
     */
    public void run(int steps) {
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < steps; i++) {
            step();
        }
        
        long endTime = System.currentTimeMillis();
        metrics.setExecutionTime(endTime - startTime);
    }
    
    /**
     * Ejecuta la simulación con visualización en consola
     */
    public void runWithVisualization(int steps, int visualizationInterval) {
        long startTime = System.currentTimeMillis();
        
        System.out.println("\n=== INICIANDO SIMULACIÓN SECUENCIAL ===");
        
        for (int i = 0; i < steps; i++) {
            step();
            
            if (i % visualizationInterval == 0) {
                System.out.println("\n------- Paso " + i + " -------");
                printSimulationState();
            }
        }
        
        long endTime = System.currentTimeMillis();
        metrics.setExecutionTime(endTime - startTime);
        
        System.out.println("\n=== SIMULACIÓN COMPLETADA ===");
        printFinalMetrics();
    }
    
    /**
     * Imprime el estado actual de la simulación
     */
    private void printSimulationState() {
        char[][] display = new char[grid.getHeight()][grid.getWidth()];
        
        // Inicializar con la rejilla
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                Position pos = new Position(x, y);
                display[y][x] = grid.getCellType(pos).getSymbol();
            }
        }
        
        // Marcar vehículos
        for (Vehicle vehicle : vehicles) {
            Position pos = vehicle.getPosition();
            if (grid.isValidPosition(pos)) {
                char symbol = vehicle.isStopped() ? 'X' : 'V';
                display[pos.getY()][pos.getX()] = symbol;
            }
        }
        
        System.out.println(metrics);
    }
    
    /**
     * Imprime métricas finales
     */
    private void printFinalMetrics() {
        System.out.println("\n" + metrics);
        System.out.println("\nDetalles de rendimiento:");
        System.out.println("  Throughput: " + 
            String.format("%.2f", (double) metrics.getTotalMoves() / (metrics.getExecutionTimeMs() / 1000.0)) + 
            " movimientos/segundo");
    }
    
    public SimulationMetrics getMetrics() {
        return metrics;
    }
    
    public Grid getGrid() {
        return grid;
    }
    
    public List<Vehicle> getVehicles() {
        return vehicles;
    }
}
