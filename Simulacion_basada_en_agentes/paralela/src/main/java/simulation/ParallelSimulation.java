package simulation;

import model.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simulación paralela de tráfico urbano basada en agentes
 * Estrategia: Paralelización por agentes
 * Los vehículos se distribuyen entre hilos para actualización concurrente
 */
public class ParallelSimulation {
    private final Grid grid;
    private final List<Vehicle> vehicles;
    private final Map<Position, TrafficLight> trafficLights;
    private final Map<Position, List<Vehicle>> occupancy;
    private final SimulationMetrics metrics;
    private final Random random;
    
    // Paralelización
    private final int numThreads;
    private final ExecutorService executorService;
    private final ReentrantLock occupancyLock = new ReentrantLock();
    
    // Parámetros de simulación
    private final int trafficLightCycleDuration;
    private final double directionChangeProb;
    private final int maxVehiclesPerCell = 2;
    
    /**
     * Constructor de la simulación paralela
     * 
     * @param gridFilePath Ruta al archivo de configuración de la rejilla
     * @param numVehicles Número de vehículos a simular
     * @param trafficLightCycleDuration Duración del ciclo de semáforos
     * @param directionChangeProb Probabilidad de cambio de dirección
     * @param numThreads Número de hilos para paralelización
     */
    public ParallelSimulation(String gridFilePath, int numVehicles, 
                              int trafficLightCycleDuration, double directionChangeProb,
                              int numThreads) throws IOException {
        this.grid = new Grid(gridFilePath);
        this.vehicles = new ArrayList<>();
        this.trafficLights = new HashMap<>();
        this.occupancy = new ConcurrentHashMap<>();
        this.metrics = new SimulationMetrics();
        this.random = new Random();
        this.trafficLightCycleDuration = trafficLightCycleDuration;
        this.directionChangeProb = directionChangeProb;
        this.numThreads = numThreads;
        this.executorService = Executors.newFixedThreadPool(numThreads);
        
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
            
            occupancy.computeIfAbsent(pos, k -> new CopyOnWriteArrayList<>()).add(vehicle);
        }
    }
    
    /**
     * Ejecuta un paso de tiempo de la simulación
     */
    public void step() throws InterruptedException, ExecutionException {
        // 1. Actualizar semáforos (secuencial, es rápido)
        updateTrafficLights();
        
        // 2. Actualizar vehículos (PARALELO)
        updateVehiclesParallel();
        
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
     * Actualiza el estado de todos los vehículos en PARALELO
     */
    private void updateVehiclesParallel() throws InterruptedException, ExecutionException {
        // Limpiar ocupación anterior
        occupancy.clear();
        
        // Estructuras compartidas para movimientos
        ConcurrentHashMap<Vehicle, Position> plannedMoves = new ConcurrentHashMap<>();
        ConcurrentHashMap<Vehicle, Direction> plannedDirections = new ConcurrentHashMap<>();
        ConcurrentHashMap<Vehicle, Position> stoppedVehicles = new ConcurrentHashMap<>();
        
        // Dividir vehículos entre hilos
        int vehiclesPerThread = (int) Math.ceil((double) vehicles.size() / numThreads);
        List<Future<?>> futures = new ArrayList<>();
        
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            final int startIdx = threadId * vehiclesPerThread;
            final int endIdx = Math.min(startIdx + vehiclesPerThread, vehicles.size());
            
            if (startIdx >= vehicles.size()) break;
            
            Future<?> future = executorService.submit(() -> {
                planMovementsForVehicles(startIdx, endIdx, plannedMoves, plannedDirections, stoppedVehicles);
            });
            
            futures.add(future);
        }
        
        // Esperar a que todos los hilos terminen
        for (Future<?> future : futures) {
            future.get();
        }
        
        // Evitar condiciones de carrera
        resolveConflictsAndExecuteMoves(plannedMoves, plannedDirections, stoppedVehicles);
    }
    
    /**
     * Movimientos para un grupo de vehículos
     */
    private void planMovementsForVehicles(int startIdx, int endIdx,
                                          ConcurrentHashMap<Vehicle, Position> plannedMoves,
                                          ConcurrentHashMap<Vehicle, Direction> plannedDirections,
                                          ConcurrentHashMap<Vehicle, Position> stoppedVehicles) {
        Random threadRandom = new Random();
        
        for (int i = startIdx; i < endIdx; i++) {
            Vehicle vehicle = vehicles.get(i);
            Position currentPos = vehicle.getPosition();
            Direction currentDir = vehicle.getDirection();
            Position nextPos = vehicle.getNextPosition();
            boolean foundValidMove = false;
            Direction chosenDir = currentDir;
            
            // Verificar si puede avanzar en su dirección actual
            if (grid.isTraversable(nextPos)) {
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
                        foundValidMove = true;
                        if (threadRandom.nextDouble() < directionChangeProb) {
                            Direction[] perp = currentDir.perpendiculars();
                            if (perp.length > 0) {
                                Direction newDir = perp[threadRandom.nextInt(perp.length)];
                                Position testPos = currentPos.move(newDir);
                                if (grid.isTraversable(testPos)) {
                                    nextPos = testPos;
                                    chosenDir = newDir;
                                }
                            }
                        }
                    }
                } else {
                    foundValidMove = true;
                }
            }
            
            // Si no puede moverse, intentar alternativas
            if (!foundValidMove) {
                if (grid.isIntersection(currentPos)) {
                    TrafficLight light = trafficLights.get(currentPos);
                    Direction[] allDirs = Direction.values();
                    List<Direction> dirList = new ArrayList<>(Arrays.asList(allDirs));
                    Collections.shuffle(dirList, threadRandom);
                    
                    for (Direction testDir : dirList) {
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
                }
            }
            
            if (foundValidMove) {
                plannedMoves.put(vehicle, nextPos);
                plannedDirections.put(vehicle, chosenDir);
            } else {
                stoppedVehicles.put(vehicle, currentPos);
            }
        }
    }
    
    /**
     * Evitar condiciones de carrera
     */
    private void resolveConflictsAndExecuteMoves(ConcurrentHashMap<Vehicle, Position> plannedMoves,
                                                 ConcurrentHashMap<Vehicle, Direction> plannedDirections,
                                                 ConcurrentHashMap<Vehicle, Position> stoppedVehicles) {
        // Primero, procesar vehículos detenidos
        for (Map.Entry<Vehicle, Position> entry : stoppedVehicles.entrySet()) {
            Vehicle vehicle = entry.getKey();
            Position pos = entry.getValue();
            vehicle.stop();
            occupancy.computeIfAbsent(pos, k -> new CopyOnWriteArrayList<>()).add(vehicle);
        }
        
        // Agrupar vehículos por destino y dirección
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
            
            for (Map.Entry<Direction, List<Vehicle>> dirEntry : dirMap.entrySet()) {
                Direction dir = dirEntry.getKey();
                List<Vehicle> vehiclesInDir = dirEntry.getValue();
                
                int allowedInDir = 1;
                
                for (int i = 0; i < Math.min(vehiclesInDir.size(), allowedInDir); i++) {
                    Vehicle vehicle = vehiclesInDir.get(i);
                    Direction newDir = plannedDirections.get(vehicle);
                    
                    if (newDir != vehicle.getDirection()) {
                        vehicle.setDirection(newDir);
                    }
                    
                    vehicle.move(targetPos);
                    occupancy.computeIfAbsent(targetPos, k -> new CopyOnWriteArrayList<>()).add(vehicle);
                }
                
                for (int i = allowedInDir; i < vehiclesInDir.size(); i++) {
                    Vehicle vehicle = vehiclesInDir.get(i);
                    vehicle.stop();
                    Position currentPos = vehicle.getPosition();
                    occupancy.computeIfAbsent(currentPos, k -> new CopyOnWriteArrayList<>()).add(vehicle);
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
    public void run(int steps) throws InterruptedException, ExecutionException {
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
    public void runWithVisualization(int steps, int visualizationInterval) 
            throws InterruptedException, ExecutionException {
        long startTime = System.currentTimeMillis();
        
        System.out.println("\n=== INICIANDO SIMULACIÓN PARALELA ===");
        System.out.println("Hilos: " + numThreads);
        System.out.println();
        
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
        
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                Position pos = new Position(x, y);
                display[y][x] = grid.getCellType(pos).getSymbol();
            }
        }
        
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
        System.out.println("  Hilos utilizados: " + numThreads);
        System.out.println("  Throughput: " + 
            String.format("%.2f", (double) metrics.getTotalMoves() / (metrics.getExecutionTimeMs() / 1000.0)) + 
            " movimientos/segundo");
    }
    
    /**
     * Cierra el ExecutorService
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
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
    
    public int getNumThreads() {
        return numThreads;
    }
}
