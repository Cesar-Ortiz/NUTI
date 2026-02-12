package model;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa la rejilla urbana donde se desarrolla la simulación
 */
public class Grid {
    private final CellType[][] cells;
    private final int width;
    private final int height;
    private final List<Position> intersections;
    
    public Grid(String filePath) throws IOException {
        List<String> lines = readGridFile(filePath);
        this.height = lines.size();
        this.width = lines.isEmpty() ? 0 : lines.get(0).length();
        this.cells = new CellType[height][width];
        this.intersections = new ArrayList<>();
        
        parseGrid(lines);
    }
    
    /**
     * Lee el archivo de configuración de la rejilla
     */
    private List<String> readGridFile(String filePath) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
    
    /**
     * Parsea el contenido del archivo y construye la rejilla
     */
    private void parseGrid(List<String> lines) {
        for (int y = 0; y < height; y++) {
            String line = lines.get(y);
            for (int x = 0; x < Math.min(line.length(), width); x++) {
                char symbol = line.charAt(x);
                CellType type = CellType.fromSymbol(symbol);
                cells[y][x] = type;
                
                if (type == CellType.INTERSECTION) {
                    intersections.add(new Position(x, y));
                }
            }
        }
    }
    
    /**
     * Verifica si una posición es válida y está dentro de los límites
     */
    public boolean isValidPosition(Position pos) {
        return pos.getX() >= 0 && pos.getX() < width &&
               pos.getY() >= 0 && pos.getY() < height;
    }
    
    /**
     * Verifica si una celda es transitable
     */
    public boolean isTraversable(Position pos) {
        if (!isValidPosition(pos)) {
            return false;
        }
        return cells[pos.getY()][pos.getX()].isTraversable();
    }
    
    /**
     * Verifica si una posición es una intersección
     */
    public boolean isIntersection(Position pos) {
        if (!isValidPosition(pos)) {
            return false;
        }
        return cells[pos.getY()][pos.getX()] == CellType.INTERSECTION;
    }
    
    /**
     * Obtiene el tipo de celda en una posición
     */
    public CellType getCellType(Position pos) {
        if (!isValidPosition(pos)) {
            return CellType.BLOCKED;
        }
        return cells[pos.getY()][pos.getX()];
    }
    
    /**
     * Retorna todas las posiciones transitables
     */
    public List<Position> getTraversablePositions() {
        List<Position> positions = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (cells[y][x].isTraversable()) {
                    positions.add(new Position(x, y));
                }
            }
        }
        return positions;
    }
    
    public List<Position> getIntersections() {
        return new ArrayList<>(intersections);
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
    
    /**
     * Imprime la rejilla en consola para debug
     */
    public void print() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                System.out.print(cells[y][x].getSymbol());
            }
            System.out.println();
        }
    }
}
