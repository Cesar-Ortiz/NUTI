package simulation;

import model.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulación paralela de tráfico urbano basada en agentes.
 *
 * Estrategia: 4 cuadrantes geográficos (NW=0, NE=1, SW=2, SE=3).
 * Cada hilo es dueño de un cuadrante
 */
public class ParallelSimulation {

    public static final int NUM_THREADS = 4;

    private final Grid grid;
    private final List<Vehicle> vehicles;
    private final Map<Position, TrafficLight> trafficLights;
    private final ConcurrentHashMap<Position, List<Vehicle>> occupancy;

    @SuppressWarnings("unchecked")
    private final List<Vehicle>[] quadrantVehicles = new List[NUM_THREADS];

    
    private ConcurrentHashMap<Vehicle, Position>  plannedMoves;
    private ConcurrentHashMap<Vehicle, Direction> plannedDirections;
    private ConcurrentHashMap<Vehicle, Boolean>   stoppedFlags;

    // Agrupación por destino — construida por el hilo principal entre fases
    // Position → (Direction → lista de vehículos que van ahí en esa dirección)
    private Map<Position, Map<Direction, List<Vehicle>>> byDestination;

    private final AtomicInteger atomicStopped    = new AtomicInteger(0);
    private final AtomicInteger atomicTotalMoves = new AtomicInteger(0);

    private final SimulationMetrics metrics;

    // Paralelización
    private final ExecutorService executor;
    private final CyclicBarrier barrier1;
    private final CyclicBarrier barrier2;

    private final int midX;
    private final int midY;
    private final int    trafficLightCycleDuration;
    private final double directionChangeProb;

    // Constructor
    public ParallelSimulation(String gridFilePath, int numVehicles,
                              int trafficLightCycleDuration,
                              double directionChangeProb) throws IOException {
        this.grid                      = new Grid(gridFilePath);
        this.vehicles                  = new ArrayList<>();
        this.trafficLights             = new HashMap<>();
        this.occupancy                 = new ConcurrentHashMap<>();
        this.metrics                   = new SimulationMetrics();
        this.trafficLightCycleDuration = trafficLightCycleDuration;
        this.directionChangeProb       = directionChangeProb;

        this.midX = grid.getWidth()  / 2;
        this.midY = grid.getHeight() / 2;

        this.executor = Executors.newFixedThreadPool(NUM_THREADS);

        this.barrier1 = new CyclicBarrier(NUM_THREADS, this::buildByDestination);
        this.barrier2 = new CyclicBarrier(NUM_THREADS);

        for (int i = 0; i < NUM_THREADS; i++) quadrantVehicles[i] = new ArrayList<>();

        initializeTrafficLights();
        initializeVehicles(numVehicles);
    }

    // Inicialización

    private void initializeTrafficLights() {
        for (Position p : grid.getIntersections())
            trafficLights.put(p, new TrafficLight(p, trafficLightCycleDuration));
    }

    private void initializeVehicles(int numVehicles) {
        List<Position> traversable = grid.getTraversablePositions();
        if (traversable.isEmpty())
            throw new IllegalStateException("No hay posiciones transitables en la rejilla");

        Random rng = new Random();
        Direction[] dirs = Direction.values();
        for (int i = 0; i < numVehicles; i++) {
            Position pos = traversable.get(rng.nextInt(traversable.size()));
            Direction dir = dirs[rng.nextInt(dirs.length)];
            Vehicle v = new Vehicle(pos, dir);
            vehicles.add(v);
            occupancy.computeIfAbsent(pos, k -> new CopyOnWriteArrayList<>()).add(v);
        }
    }

    // Cuadrantes

    private int quadrantOf(Position pos) {
        boolean west  = pos.getX() < midX;
        boolean north = pos.getY() < midY;
        if (north && west) return 0; // NW
        if (north)         return 1; // NE
        if (west)          return 2; // SW
        return                    3; // SE
    }


    private void buildByDestination() {
        Map<Position, Map<Direction, List<Vehicle>>> map = new HashMap<>();
        for (Map.Entry<Vehicle, Position> e : plannedMoves.entrySet()) {
            Vehicle v   = e.getKey();
            Position dest = e.getValue();
            Direction d = plannedDirections.get(v);
            map.computeIfAbsent(dest, k -> new HashMap<>())
               .computeIfAbsent(d, k -> new ArrayList<>())
               .add(v);
        }
        byDestination = map; // visible a todos los hilos
    }

    // Paso de simulación

    public void step() throws InterruptedException, ExecutionException {
        // Semáforos: secuencial, O(intersecciones), muy rápido
        for (TrafficLight tl : trafficLights.values()) tl.update();

        // Reasignar vehículos a cuadrantes (O(n) secuencial, una sola pasada)
        for (int i = 0; i < NUM_THREADS; i++) quadrantVehicles[i] = new ArrayList<>();
        for (Vehicle v : vehicles) quadrantVehicles[quadrantOf(v.getPosition())].add(v);

        // Reiniciar estructuras compartidas
        plannedMoves      = new ConcurrentHashMap<>();
        plannedDirections = new ConcurrentHashMap<>();
        stoppedFlags      = new ConcurrentHashMap<>();
        occupancy.clear();
        atomicStopped.set(0);
        atomicTotalMoves.set(0);

        // Lanzar los 4 hilos de cuadrante
        List<Future<?>> futures = new ArrayList<>(NUM_THREADS);
        for (int q = 0; q < NUM_THREADS; q++) {
            final int quadrant = q;
            futures.add(executor.submit(() -> runQuadrantWorker(quadrant)));
        }
        for (Future<?> f : futures) f.get();

        metrics.update(vehicles.size(), atomicStopped.get(), atomicTotalMoves.get());
    }


    private void runQuadrantWorker(int quadrant) {
        try {
            Random rng = new Random();

            for (Vehicle v : quadrantVehicles[quadrant]) {
                planVehicle(v, rng);
            }

            barrier1.await();

            for (Map.Entry<Position, Map<Direction, List<Vehicle>>> entry
                    : byDestination.entrySet()) {
                Position dest = entry.getKey();
                if (quadrantOf(dest) != quadrant) continue;
                resolveDestination(dest, entry.getValue());
            }
            for (Map.Entry<Vehicle, Boolean> entry : stoppedFlags.entrySet()) {
                Vehicle v = entry.getKey();
                if (quadrantOf(v.getPosition()) != quadrant) continue;
                v.stop();
                occupancy.computeIfAbsent(v.getPosition(),
                        k -> new CopyOnWriteArrayList<>()).add(v);
            }

            barrier2.await();

            int localStopped = 0;
            int localMoves   = 0;
            // ya se movieron a nuevas posiciones; iteramos vehicles completo
            // pero dividido por índice para evitar solapamiento
            int chunk = (int) Math.ceil((double) vehicles.size() / NUM_THREADS);
            int from  = quadrant * chunk;
            int to    = Math.min(from + chunk, vehicles.size());
            for (int i = from; i < to; i++) {
                Vehicle v = vehicles.get(i);
                if (v.isStopped()) localStopped++;
                localMoves += v.getTotalMoves();
            }
            atomicStopped.addAndGet(localStopped);
            atomicTotalMoves.addAndGet(localMoves);

        } catch (InterruptedException | BrokenBarrierException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void planVehicle(Vehicle v, Random rng) {
        Position currentPos = v.getPosition();
        Direction currentDir = v.getDirection();
        Position nextPos    = v.getNextPosition();
        Direction chosenDir = currentDir;
        boolean canMove     = false;

        if (grid.isTraversable(nextPos)) {
            if (grid.isIntersection(currentPos)) {
                TrafficLight tl = trafficLights.get(currentPos);
                if (tl != null && !tl.canPass(currentDir)) {
                    for (Direction d : currentDir.perpendiculars()) {
                        if (tl.canPass(d)) {
                            Position tp = currentPos.move(d);
                            if (grid.isTraversable(tp)) {
                                nextPos = tp; chosenDir = d; canMove = true; break;
                            }
                        }
                    }
                } else {
                    canMove = true;
                    if (rng.nextDouble() < directionChangeProb) {
                        Direction[] perp = currentDir.perpendiculars();
                        if (perp.length > 0) {
                            Direction nd = perp[rng.nextInt(perp.length)];
                            Position tp = currentPos.move(nd);
                            if (grid.isTraversable(tp)) { nextPos = tp; chosenDir = nd; }
                        }
                    }
                }
            } else {
                canMove = true;
            }
        }

        if (!canMove && grid.isIntersection(currentPos)) {
            TrafficLight tl = trafficLights.get(currentPos);
            List<Direction> dirList = new ArrayList<>(Arrays.asList(Direction.values()));
            Collections.shuffle(dirList, rng);
            for (Direction d : dirList) {
                if (d == currentDir.opposite()) continue;
                if (tl == null || tl.canPass(d)) {
                    Position tp = currentPos.move(d);
                    if (grid.isTraversable(tp)) {
                        nextPos = tp; chosenDir = d; canMove = true; break;
                    }
                }
            }
            if (!canMove) {
                Direction rev = currentDir.opposite();
                if (tl == null || tl.canPass(rev)) {
                    Position rp = currentPos.move(rev);
                    if (grid.isTraversable(rp)) {
                        nextPos = rp; chosenDir = rev; canMove = true;
                    }
                }
            }
        }

        if (canMove) {
            plannedMoves.put(v, nextPos);
            plannedDirections.put(v, chosenDir);
        } else {
            stoppedFlags.put(v, Boolean.TRUE);
        }
    }

    private void resolveDestination(Position dest,
                                    Map<Direction, List<Vehicle>> byDir) {
        for (Map.Entry<Direction, List<Vehicle>> de : byDir.entrySet()) {
            List<Vehicle> list = de.getValue();
            Vehicle winner = list.get(0);
            Direction nd = plannedDirections.get(winner);
            if (nd != winner.getDirection()) winner.setDirection(nd);
            winner.move(dest);
            occupancy.computeIfAbsent(dest, k -> new CopyOnWriteArrayList<>()).add(winner);

            for (int i = 1; i < list.size(); i++) {
                Vehicle loser = list.get(i);
                loser.stop();
                occupancy.computeIfAbsent(loser.getPosition(),
                        k -> new CopyOnWriteArrayList<>()).add(loser);
            }
        }
    }


    public void run(int steps) throws InterruptedException, ExecutionException {
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < steps; i++) step();
        metrics.setExecutionTime(System.currentTimeMillis() - t0);
    }

    public void runWithVisualization(int steps, int interval)
            throws InterruptedException, ExecutionException {
        System.out.println("\n=== INICIANDO SIMULACION PARALELA ===\n");
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < steps; i++) {
            step();
            if (i % interval == 0) {
                System.out.println("------- Paso " + i + " -------");
                System.out.println(metrics);
                System.out.println();
            }
        }
        metrics.setExecutionTime(System.currentTimeMillis() - t0);
        System.out.println("\n=== SIMULACIÓN COMPLETADA ===");
        printFinalMetrics();
    }

    private void printFinalMetrics() {
        System.out.println("\n" + metrics);
        System.out.println("\nDetalles de rendimiento:");
        System.out.println("  Hilos (cuadrantes): " + NUM_THREADS);
        System.out.printf("  Throughput: %.2f movimientos/segundo%n",
                (double) metrics.getTotalMoves() / (metrics.getExecutionTimeMs() / 1000.0));
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS))
                executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    public SimulationMetrics getMetrics()   { return metrics; }
    public Grid              getGrid()      { return grid; }
    public List<Vehicle>     getVehicles()  { return vehicles; }
    public int               getNumThreads(){ return NUM_THREADS; }
}
