import simulation.SequentialSimulation;

import java.io.IOException;

/**
 * Clase principal para ejecutar la simulación de tráfico urbano
 */
public class Main {
    public static void main(String[] args) {
        // Parámetros de configuración
        String gridFile = "grid.txt";
        int numVehicles = 50000;
        int simulationSteps = 100;
        int trafficLightCycle = 10;
        double directionChangeProb = 0.2;
        int visualizationInterval = 20;
        
        try {
            // Crear y ejecutar simulación
            SequentialSimulation simulation = new SequentialSimulation(
                gridFile, 
                numVehicles, 
                trafficLightCycle, 
                directionChangeProb
            );

            long seqStartTime = System.currentTimeMillis();
            simulation.run(simulationSteps);
            long seqEndTime = System.currentTimeMillis();

            System.out.println("═══════════════════════════════════════════════════════════");
            System.out.println("   SIMULACIÓN BASADA EN AGENTES DE TRÁFICO URBANO          ");
            System.out.println("               Versión Secuencial                          ");
            System.out.println("═══════════════════════════════════════════════════════════\n");
            
            System.out.println("Configuración:");
            System.out.println("  Archivo de rejilla: " + gridFile);
            System.out.println("  Número de vehículos: " + numVehicles);
            System.out.println("  Pasos de simulación: " + simulationSteps);
            System.out.println("  Ciclo de semáforo: " + trafficLightCycle + " pasos");
            System.out.println("  Probabilidad de cambio de dirección: " + (directionChangeProb * 100) + "%");
            System.out.println();
            
            // Mostrar la rejilla inicial
            System.out.println("Rejilla urbana:");
            simulation.getGrid().print();
            System.out.println();
            
            // Ejecutar simulación con visualización
            simulation.runWithVisualization(simulationSteps, visualizationInterval);
            
            // Exportar datos para análisis
            exportMetrics(simulation);
            
        } catch (IOException e) {
            System.err.println("Error al cargar el archivo de rejilla: " + e.getMessage());
            System.err.println("Asegúrese de que el archivo '" + gridFile + "' existe en el directorio actual.");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error durante la simulación: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Exporta métricas para análisis posterior
     */
    private static void exportMetrics(SequentialSimulation simulation) {
        System.out.println("\n═════════════════════════════════════════════════════════════");
        System.out.println("                    RESUMEN DE MÉTRICAS                        ");
        System.out.println("═════════════════════════════════════════════════════════════");
        System.out.println("\nMétricas del Sistema:");
        System.out.println("  - Flujo promedio de vehículos: " + 
            String.format("%.2f", simulation.getMetrics().getAverageFlow()));
        System.out.println("  - Vehículos detenidos: " + 
            simulation.getMetrics().getStoppedVehicles() + " / " + 
            simulation.getMetrics().getTotalVehicles());
        System.out.println("  - Porcentaje de congestión: " + 
            String.format("%.2f%%", simulation.getMetrics().getStopPercentage()));
        
        System.out.println("\nMétricas Computacionales:");
        System.out.println("  - Tiempo de ejecución: " + 
            simulation.getMetrics().getExecutionTimeMs() + " ms");
        System.out.println("  - Movimientos totales: " + 
            simulation.getMetrics().getTotalMoves());
    }
}
