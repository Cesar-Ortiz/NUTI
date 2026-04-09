import simulation.SequentialSimulation;

import java.io.IOException;

/**
 * Clase principal para ejecutar la simulación secuencial de tráfico urbano
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
            SequentialSimulation simulation = new SequentialSimulation(
                gridFile, numVehicles, trafficLightCycle, directionChangeProb
            );

            System.out.println("═══════════════════════════════════════════════════════════");
            System.out.println("   SIMULACIÓN BASADA EN AGENTES DE TRÁFICO URBANO          ");
            System.out.println("               Versión Secuencial                          ");
            System.out.println("═══════════════════════════════════════════════════════════\n");

            System.out.println("Configuración:");
            System.out.println("  Archivo de rejilla:               " + gridFile);
            System.out.println("  Número de vehículos:              " + numVehicles);
            System.out.println("  Pasos de simulación:              " + simulationSteps);
            System.out.println("  Ciclo de semáforo:                " + trafficLightCycle + " pasos");
            System.out.println("  Probabilidad de cambio de dirección: " + (directionChangeProb * 100) + "%");
            System.out.println();

            System.out.println("Rejilla urbana:");
            simulation.getGrid().print();
            System.out.println();

            // Una sola ejecución con visualización
            simulation.runWithVisualization(simulationSteps, visualizationInterval);

            exportMetrics(simulation);

        } catch (IOException e) {
            System.err.println("Error al cargar el archivo de rejilla: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error durante la simulación: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void exportMetrics(SequentialSimulation simulation) {
        System.out.println("\n═════════════════════════════════════════════════════════════");
        System.out.println("                    RESUMEN DE MÉTRICAS                        ");
        System.out.println("═════════════════════════════════════════════════════════════");
        System.out.println("\nMétricas del Sistema:");
        System.out.printf("  - Flujo promedio de vehículos:  %.2f%n",
                simulation.getMetrics().getAverageFlow());
        System.out.println("  - Vehículos detenidos:          " +
                simulation.getMetrics().getStoppedVehicles() + " / " +
                simulation.getMetrics().getTotalVehicles());
        System.out.printf("  - Porcentaje de congestión:     %.2f%%%n",
                simulation.getMetrics().getStopPercentage());
        System.out.println("\nMétricas Computacionales:");
        System.out.println("  - Tiempo de ejecución:          " +
                simulation.getMetrics().getExecutionTimeMs() + " ms");
        System.out.println("  - Movimientos totales:          " +
                simulation.getMetrics().getTotalMoves());
    }
}
