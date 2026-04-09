import simulation.ParallelSimulation;

import java.io.IOException;

/**
 * Clase principal para ejecutar la simulación paralela de tráfico urbano.
 * La versión paralela usa 4 hilos, uno por cuadrante geográfico (NW, NE, SW, SE).
 */
public class Main {
    public static void main(String[] args) {
        String gridFile            = "grid.txt";
        int    numVehicles         = 2000;
        int    simulationSteps     = 200;
        int    trafficLightCycle   = 10;
        double directionChangeProb = 0.2;
        int    visualizationInterval = 50;

        try {
            ParallelSimulation simulation = new ParallelSimulation(
                gridFile, numVehicles, trafficLightCycle, directionChangeProb
            );

            System.out.println("═══════════════════════════════════════════════════════════");
            System.out.println("   SIMULACIÓN BASADA EN AGENTES DE TRÁFICO URBANO          ");
            System.out.println("               Versión Paralela                            ");
            System.out.println("═══════════════════════════════════════════════════════════\n");

            System.out.println("Configuración:");
            System.out.println("  Archivo de rejilla:               " + gridFile);
            System.out.println("  Número de vehículos:              " + numVehicles);
            System.out.println("  Pasos de simulación:              " + simulationSteps);
            System.out.println("  Ciclo de semáforo:                " + trafficLightCycle + " pasos");
            System.out.println("  Prob. cambio de dirección:        " + (directionChangeProb * 100) + "%");
            System.out.println("  Hilos (cuadrantes):               " + ParallelSimulation.NUM_THREADS);
            System.out.println();

            System.out.println("Rejilla urbana:");
            simulation.getGrid().print();
            System.out.println();

            // Una sola ejecución con visualización
            simulation.runWithVisualization(simulationSteps, visualizationInterval);

            printMetrics(simulation);
            simulation.shutdown();

        } catch (IOException e) {
            System.err.println("Error al cargar el archivo de rejilla: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error durante la simulación: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printMetrics(ParallelSimulation sim) {
        System.out.println("\n═════════════════════════════════════════════════════════════");
        System.out.println("                    RESUMEN DE MÉTRICAS                        ");
        System.out.println("═════════════════════════════════════════════════════════════");
        System.out.println("\nMétricas del Sistema:");
        System.out.printf("  - Flujo promedio de vehículos:  %.2f%n",
                sim.getMetrics().getAverageFlow());
        System.out.println("  - Vehículos detenidos:          " +
                sim.getMetrics().getStoppedVehicles() + " / " +
                sim.getMetrics().getTotalVehicles());
        System.out.printf("  - Porcentaje de congestión:     %.2f%%%n",
                sim.getMetrics().getStopPercentage());
        System.out.println("\nMétricas Computacionales:");
        System.out.println("  - Tiempo de ejecución:          " +
                sim.getMetrics().getExecutionTimeMs() + " ms");
        System.out.println("  - Movimientos totales:          " +
                sim.getMetrics().getTotalMoves());
    }
}
