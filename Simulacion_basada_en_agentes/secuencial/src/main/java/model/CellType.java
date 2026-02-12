package model;

/**
 * Tipos de celdas en la rejilla
 */
public enum CellType {
    STREET('.'),           // Calle transitable
    INTERSECTION('+'),     // Intersección con semáforo
    BLOCKED('#');          // Espacio no transitable
    
    private final char symbol;
    
    CellType(char symbol) {
        this.symbol = symbol;
    }
    
    public char getSymbol() {
        return symbol;
    }
    
    /**
     * Determina si la celda es transitable
     */
    public boolean isTraversable() {
        return this == STREET || this == INTERSECTION;
    }
    
    /**
     * Obtiene el tipo de celda desde un símbolo
     */
    public static CellType fromSymbol(char symbol) {
        for (CellType type : values()) {
            if (type.symbol == symbol) {
                return type;
            }
        }
        throw new IllegalArgumentException("Símbolo de celda inválido: " + symbol);
    }
}
